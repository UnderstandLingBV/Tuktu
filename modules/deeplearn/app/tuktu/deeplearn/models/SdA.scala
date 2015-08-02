package tuktu.deeplearn.models

/**
 * Implementation by Yusuke Sugomori - https://github.com/yusugomori/DeepLearning
 */

import scala.util.Random
import scala.math

class SdA(val N: Int, val n_ins: Int, hidden_layer_sizes: Array[Int], val n_outs: Int, val n_layers: Int, var rng: Random = null) {

    def sigmoid(x: Double): Double = {
        return 1.0 / (1.0 + math.pow(math.E, -x))
    }

    var input_size: Int = 0

    // var hidden_layer_sizes: Array[Int] = new Array[Int](n_layers)
    var sigmoid_layers: Array[HiddenLayer] = new Array[HiddenLayer](n_layers)
    var dA_layers: Array[dA] = new Array[dA](n_layers)

    if (rng == null) rng = new Random(1234)

    var i: Int = 0

    // construct multi-layer
    for (i <- 0 until n_layers) {
        if (i == 0) {
            input_size = n_ins
        } else {
            input_size = hidden_layer_sizes(i - 1)
        }

        // construct sigmoid_layer
        sigmoid_layers(i) = new HiddenLayer(N, input_size, hidden_layer_sizes(i), null, null, rng)

        // construct dA_layer
        dA_layers(i) = new dA(N, input_size, hidden_layer_sizes(i), sigmoid_layers(i).W, sigmoid_layers(i).b, null, rng)
    }

    // layer for output using LogisticRegression
    val log_layer = new LogisticRegression(N, hidden_layer_sizes(n_layers - 1), n_outs)

    def pretrain(train_X: Array[Array[Int]], lr: Double, corruption_level: Double, epochs: Int) {
        var layer_input: Array[Int] = new Array[Int](0)
        var prev_layer_input_size: Int = 0
        var prev_layer_input: Array[Int] = new Array[Int](0)

        var i: Int = 0
        var j: Int = 0
        var epoch: Int = 0
        var n: Int = 0
        var l: Int = 0

        for (i <- 0 until n_layers) { // layer-wise
            for (epoch <- 0 until epochs) { // training epochs
                for (n <- 0 until N) { // input x1...xN
                    // layer input
                    for (l <- 0 to i) {
                        if (l == 0) {
                            layer_input = new Array[Int](n_ins)
                            for (j <- 0 until n_ins) layer_input(j) = train_X(n)(j)
                        } else {
                            if (l == 1) prev_layer_input_size = n_ins
                            else prev_layer_input_size = hidden_layer_sizes(l - 2)

                            prev_layer_input = new Array[Int](prev_layer_input_size)
                            for (j <- 0 until prev_layer_input_size) prev_layer_input(j) = layer_input(j)

                            layer_input = new Array[Int](hidden_layer_sizes(l - 1))

                            sigmoid_layers(l - 1).sample_h_given_v(prev_layer_input, layer_input)
                        }
                    }

                    dA_layers(i).train(layer_input, lr, corruption_level)
                }
            }
        }

    }

    def finetune(train_X: Array[Array[Int]], train_Y: Array[Array[Int]], lr: Double, epochs: Int) {
        var layer_input: Array[Int] = new Array[Int](0)
        var prev_layer_input: Array[Int] = new Array[Int](0)

        var epoch: Int = 0
        var n: Int = 0

        for (epoch <- 0 until epochs) {
            for (n <- 0 until N) {

                // layer input
                for (i <- 0 until n_layers) {
                    if (i == 0) {
                        prev_layer_input = new Array[Int](n_ins)
                        for (j <- 0 until n_ins) prev_layer_input(j) = train_X(n)(j)
                    } else {
                        prev_layer_input = new Array[Int](hidden_layer_sizes(i - 1))
                        for (j <- 0 until hidden_layer_sizes(i - 1)) prev_layer_input(j) = layer_input(j)
                    }

                    layer_input = new Array[Int](hidden_layer_sizes(i))
                    sigmoid_layers(i).sample_h_given_v(prev_layer_input, layer_input)
                }

                log_layer.train(layer_input, train_Y(n), lr)
            }
            // lr *= 0.95
        }
    }

    def predict(x: Array[Int], y: Array[Double]) {
        var layer_input: Array[Double] = new Array[Double](0)
        var prev_layer_input: Array[Double] = new Array[Double](n_ins)

        var j: Int = 0
        for (j <- 0 until n_ins) prev_layer_input(j) = x(j)

        var linear_output: Double = 0.0

        // layer activation
        var i: Int = 0
        var k: Int = 0

        for (i <- 0 until n_layers) {
            layer_input = new Array[Double](sigmoid_layers(i).n_out)

            for (k <- 0 until sigmoid_layers(i).n_out) {
                linear_output = 0.0

                for (j <- 0 until sigmoid_layers(i).n_in) {
                    linear_output += sigmoid_layers(i).W(k)(j) * prev_layer_input(j)
                }
                linear_output += sigmoid_layers(i).b(k)
                layer_input(k) = sigmoid(linear_output)
            }

            if (i < n_layers - 1) {
                prev_layer_input = new Array[Double](sigmoid_layers(i).n_out)
                for (j <- 0 until sigmoid_layers(i).n_out) prev_layer_input(j) = layer_input(j)
            }
        }

        for (i <- 0 until log_layer.n_out) {
            y(i) = 0
            for (j <- 0 until log_layer.n_in) {
                y(i) += log_layer.W(i)(j) * layer_input(j)
            }
            y(i) += log_layer.b(i)
        }

        log_layer.softmax(y)
    }

}