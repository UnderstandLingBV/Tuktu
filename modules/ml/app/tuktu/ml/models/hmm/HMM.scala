package tuktu.ml.models.hmm

/**
 * Implements a Hidden Markov Model (HMM)
 * 
 * Code taken from https://github.com/balshor/shimm
 * 
 * @param numberOfStates The number of hidden states
 * @param numberOfObservations The number of observed states
 */
class HiddenMarkovModel(
    val numberOfStates: Int,
    val numberOfObservations: Int) {

    import java.util.Random

    val A = new TransitionProbabilities(numberOfStates)
    val B = new ObservationProbabilities(numberOfStates, numberOfObservations)
    val Pi = new InitialStateDistribution(numberOfStates)

    /* Randomly generates a tuple of (observations, states) */
    def apply(numTransitions: Int, startingState: Option[Int] = None)(implicit random: Random): (IndexedSeq[Int], IndexedSeq[Int]) = {
        import scala.collection.mutable._
        val observations = new ArrayBuffer[Int](numTransitions)
        val states = new ArrayBuffer[Int](numTransitions)

        var state = startingState getOrElse Pi.getInitialState(random.nextDouble)
        states.append(state)
        observations.append(B.getObservation(state, random.nextDouble))
        (1 until numTransitions) foreach { _ =>
            state = A.getNextState(state, random.nextDouble)
            states.append(state)
            observations.append(B.getObservation(state, random.nextDouble))
        }

        (observations, states)
    }

    def normalize() {
        A.normalize()
        B.normalize()
        Pi.normalize()
    }

    def prettyPrint(w: java.io.PrintStream) {
        import w.{ print, println }
        (0 to numberOfStates - 1) foreach { row =>
            if (row == 0) {
                print("A: [ ")
            } else {
                print("     ")
            }
            print(A.data(row) mkString ("[", " ", "]"))
            if (row == numberOfStates - 1) {
                print(" ]")
            }
            println
        }
        (0 to numberOfStates - 1) foreach { row =>
            if (row == 0) {
                print("B: [ ")
            } else {
                print("     ")
            }
            print(B.data(row) mkString ("[", " ", "]"))
            if (row == numberOfStates - 1) {
                print(" ]")
            }
            println
        }
        print("Pi:  ")
        print(Pi.weights mkString ("[", " ", "]"))
        println
    }
}

/**
 * A = \{ a_{ij} \} State transition probability matrix.
 * a_{ij} = P(q_{t+1} = S_j | q_t = S_i), ie the probability of transitioning from state i to state j
 */
class TransitionProbabilities(val numberOfStates: Int) {
    val data: Array[Array[Double]] = Array.ofDim(numberOfStates, numberOfStates)

    def apply(i: Int, j: Int): Double = data(i)(j)
    def update(i: Int, j: Int, value: Double) = {
        data(i)(j) = value
    }

    def normalize() {
        val sums = data map (_.sum)

        sums indexOf (0.0) match {
            case x if x >= 0 => throw new IllegalStateException("Cannot normalize zero transition probabilities for index " + x)
            case -1          => // noop
        }

        (0 until numberOfStates) foreach { i =>
            (0 until numberOfStates) foreach { j =>
                data(i)(j) /= sums(i)
            }
        }
    }

    def getNextState(currentState: Int, random: Double) = {
        if (random < 0.0 || random > 1.0) {
            throw new IllegalArgumentException("Must supply a random double between 0.0 and 1.0 inclusive.")
        }
        val transitionDistribution = data(currentState)
        val weightedRandom = random * transitionDistribution.sum

        var accumulated = 0.0
        var nextState = 0
        while (accumulated < weightedRandom) {
            accumulated += transitionDistribution(nextState)
            nextState += 1
        }
        nextState - 1
    }
}

/**
 * B = \{ b_j(k) \} observation probabilities.
 * b_j(k) = P(v_k at t | q_t = S_j), ie the probabily of seeing observation k at state j
 */
class ObservationProbabilities(numberOfStates: Int, numberOfObservations: Int) {
    val data: Array[Array[Double]] = Array.ofDim(numberOfStates, numberOfObservations)

    def apply(stateIndex: Int, observationIndex: Int): Double = data(stateIndex)(observationIndex)
    def update(stateIndex: Int, observationIndex: Int, value: Double) = {
        data(stateIndex)(observationIndex) = value
    }

    def normalize() {
        val sums = data map (_.sum)

        sums indexOf (0.0) match {
            case x if x >= 0 => throw new IllegalStateException("Cannot normalize zero observation probabilities for index " + x)
            case -1          => // noop
        }

        (0 until numberOfStates) foreach { i =>
            (0 until numberOfObservations) foreach { j =>
                data(i)(j) /= sums(i)
            }
        }
    }

    def getObservation(currentState: Int, random: Double) = {
        if (random < 0.0 || random > 1.0) {
            throw new IllegalArgumentException("Must supply a random double between 0.0 and 1.0 inclusive.")
        }
        val observationDistribution = data(currentState)
        val weightedRandom = random * observationDistribution.sum

        var accumulated = 0.0
        var observation = 0
        while (accumulated < weightedRandom) {
            accumulated += observationDistribution(observation)
            observation += 1
        }
        observation - 1
    }

}

/**
 * \pi Initial state probability distribution.
 * \pi_i = P(q_1 = S_i), ie the probability that the first state is i.
 */
class InitialStateDistribution(val numberOfStates: Int) {
    val weights = Array.fill(numberOfStates)(0.0)

    def apply(i: Int) = weights(i)
    def update(i: Int, value: Double) = {
        weights(i) = value
    }

    def normalize() {
        val sum = weights sum

        if (sum == 0) {
            throw new IllegalStateException("Cannot normalize with zero total weights.")
        }
        (0 until numberOfStates) foreach { i =>
            weights(i) /= sum
        }
    }

    def getInitialState(random: Double) = {
        if (random < 0.0 || random > 1.0) {
            throw new IllegalArgumentException("Must supply a random double between 0.0 and 1.0 inclusive.")
        }
        val weightedRandom = random * weights.sum

        var accumulated = 0.0
        var state = 0
        while (accumulated < weightedRandom) {
            accumulated += weights(state)
            state += 1
        }
        state - 1
    }
}