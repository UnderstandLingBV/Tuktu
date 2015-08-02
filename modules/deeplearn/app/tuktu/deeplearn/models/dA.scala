package tuktu.deeplearn.models

/**
 * Implementation by Yusuke Sugomori - https://github.com/yusugomori/DeepLearning
 */

import scala.util.Random
import scala.math

class dA(val N: Int, val n_visible: Int, val n_hidden: Int,
         _W: Array[Array[Double]] = null, _hbias: Array[Double] = null, _vbias: Array[Double] = null,
         var rng: Random = null) {

    var W: Array[Array[Double]] = Array.ofDim[Double](n_hidden, n_visible)
    var hbias: Array[Double] = new Array[Double](n_hidden)
    var vbias: Array[Double] = new Array[Double](n_visible)

    if (rng == null) rng = new Random(1234)

    if (_W == null) {
        var i: Int = 0
        var j: Int = 0

        val a: Double = 1 / n_visible
        for (i <- 0 until n_hidden)
            for (j <- 0 until n_visible)
                W(i)(j) = uniform(-a, a)

    } else {
        W = _W
    }

    if (_hbias == null) {
        var i: Int = 0
        for (i <- 0 until n_hidden) hbias(i) = 0
    } else {
        hbias = _hbias
    }

    if (_vbias == null) {
        var i: Int = 0
        for (i <- 0 until n_visible) vbias(i) = 0
    } else {
        vbias = _vbias
    }

    def uniform(min: Double, max: Double): Double = rng.nextDouble() * (max - min) + min
    def binomial(n: Int, p: Double): Int = {
        if (p < 0 || p > 1) return 0

        var c: Int = 0
        var r: Double = 0

        var i: Int = 0
        for (i <- 0 until n) {
            r = rng.nextDouble()
            if (r < p) c += 1
        }

        c
    }

    def sigmoid(x: Double): Double = 1.0 / (1.0 + math.pow(math.E, -x))

    def get_corrupted_input(x: Array[Int], tilde_x: Array[Int], p: Double) {
        var i: Int = 0;
        for (i <- 0 until n_visible) {
            if (x(i) == 0) {
                tilde_x(i) = 0;
            } else {
                tilde_x(i) = binomial(1, p)
            }
        }
    }

    // Encode
    def get_hidden_values(x: Array[Int], y: Array[Double]) {
        var i: Int = 0
        var j: Int = 0
        for (i <- 0 until n_hidden) {
            y(i) = 0
            for (j <- 0 until n_visible) {
                y(i) += W(i)(j) * x(j)
            }
            y(i) += hbias(i)
            y(i) = sigmoid(y(i))
        }
    }

    // Decode
    def get_reconstructed_input(y: Array[Double], z: Array[Double]) {
        var i: Int = 0
        var j: Int = 0
        for (i <- 0 until n_visible) {
            z(i) = 0
            for (j <- 0 until n_hidden) {
                z(i) += W(j)(i) * y(j)
            }
            z(i) += vbias(i)
            z(i) = sigmoid(z(i))
        }
    }

    def train(x: Array[Int], lr: Double, corruption_level: Double) {
        var i: Int = 0
        var j: Int = 0

        val tilde_x: Array[Int] = new Array[Int](n_visible)
        val y: Array[Double] = new Array[Double](n_hidden)
        val z: Array[Double] = new Array[Double](n_visible)

        val L_vbias: Array[Double] = new Array[Double](n_visible)
        val L_hbias: Array[Double] = new Array[Double](n_hidden)

        val p: Double = 1 - corruption_level

        get_corrupted_input(x, tilde_x, p)
        get_hidden_values(tilde_x, y)
        get_reconstructed_input(y, z)

        // vbias
        for (i <- 0 until n_visible) {
            L_vbias(i) = x(i) - z(i)
            vbias(i) += lr * L_vbias(i) / N
        }

        // hbias
        for (i <- 0 until n_hidden) {
            L_hbias(i) = 0
            for (j <- 0 until n_visible) {
                L_hbias(i) += W(i)(j) * L_vbias(j)
            }
            L_hbias(i) *= y(i) * (1 - y(i))
            hbias(i) += lr * L_hbias(i) / N
        }

        // W
        for (i <- 0 until n_hidden) {
            for (j <- 0 until n_visible) {
                W(i)(j) += lr * (L_hbias(i) * tilde_x(j) + L_vbias(j) * y(i)) / N
            }
        }
    }

    def reconstruct(x: Array[Int], z: Array[Double]) {
        val y: Array[Double] = new Array[Double](n_hidden)

        get_hidden_values(x, y)
        get_reconstructed_input(y, z)
    }

}