package tuktu.deeplearn.models

/**
 * Implementation by Yusuke Sugomori - https://github.com/yusugomori/DeepLearning
 */

import scala.math

class LogisticRegression(val N: Int, val n_in: Int, val n_out: Int) {

    val W: Array[Array[Double]] = Array.ofDim[Double](n_out, n_in)
    val b: Array[Double] = new Array[Double](n_out)

    def train(x: Array[Int], y: Array[Int], lr: Double) {
        val p_y_given_x: Array[Double] = new Array[Double](n_out)
        val dy: Array[Double] = new Array[Double](n_out)

        var i: Int = 0
        var j: Int = 0
        for (i <- 0 until n_out) {
            p_y_given_x(i) = 0
            for (j <- 0 until n_in) {
                p_y_given_x(i) += W(i)(j) * x(j)
            }
            p_y_given_x(i) += b(i)
        }
        softmax(p_y_given_x)

        for (i <- 0 until n_out) {
            dy(i) = y(i) - p_y_given_x(i)

            for (j <- 0 until n_in) {
                W(i)(j) += lr * dy(i) * x(j) / N
            }
            b(i) += lr * dy(i) / N
        }
    }

    def softmax(x: Array[Double]) {
        var max: Double = 0.0
        var sum: Double = 0.0

        var i: Int = 0
        for (i <- 0 until n_out) if (max < x(i)) max = x(i)

        for (i <- 0 until n_out) {
            x(i) = math.exp(x(i) - max)
            sum += x(i)
        }

        for (i <- 0 until n_out) x(i) /= sum
    }

    def predict(x: Array[Int], y: Array[Double]) {
        var i: Int = 0
        var j: Int = 0
        for (i <- 0 until n_out) {
            y(i) = 0
            for (j <- 0 until n_in) {
                y(i) += W(i)(j) * x(j)
            }
            y(i) += b(i)
        }
        softmax(y)
    }

}