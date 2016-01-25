package tuktu.ml.models.svm

import tuktu.ml.models.BaseModel
import tuktu.ml.models.smileio.Operators
import smile.classification.SVM
import smile.math.kernel._

class SupportVectorMachine extends BaseModel with Operators {
    var model: SVM[Array[Double]] = _
    
    def train(data: Array[Array[Double]], labels: Array[Int], penalty: Double, strategyString: String, kernel: String, kernelParams: List[Double]) = {
        val strategy = if (strategyString == "one_vs_one") SVM.Multiclass.ONE_VS_ONE else SVM.Multiclass.ONE_VS_ALL
        val krnl = {
            kernel match {
                case "gaussian" => new GaussianKernel(kernelParams.head)
                case "hellinger" => new HellingerKernel
                case "hyperbolictangent" => new HyperbolicTangentKernel(kernelParams(0), kernelParams(1))
                case "laplacian" => new LaplacianKernel(kernelParams.head)
                case "linear" => new LinearKernel
                case "pearson" => new PearsonKernel(kernelParams(0), kernelParams(1))
                case "polynomial" => new PolynomialKernel(kernelParams.head.toInt)
                //case "sparsegaussian" => new SparseGaussianKernel(kernelParams.head)
                //case "sparsehyperbolictangent" => new SparseHyperbolicTangentKernel(kernelParams(0), kernelParams(1))
                //case "sparselaplacian" => new SparseLaplacianKernel(kernelParams.head)
                //case "sparselinear" => new SparseLinearKernel
                //case "sparsepolynomial" => new SparsePolynomialKernel(kernelParams.head.toInt)
                //case "sparsethinplatespline" => new SparseThinPlateSplineKernel(kernelParams.head)
                case "thinplatespline" => new ThinPlateSplineKernel(kernelParams.head)
                case _ => new LinearKernel
            }
        }
        model = new SVM[Array[Double]](krnl, penalty, labels.max + 1, strategy)
        model.learn(data, labels)
        model.finish
    }
    
    def classify(data: Array[Double]) =
        model.predict(data)
        
    override def serialize(filename: String) = write(model, filename)
    
    override def deserialize(filename: String) = { model = read(filename).asInstanceOf[SVM[Array[Double]]] }
}