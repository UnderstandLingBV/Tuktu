package tuktu.deeplearn.models

import tuktu.ml.models.BaseModel
import com.github.neuralnetworks.architecture.types.Autoencoder
import com.github.neuralnetworks.architecture.types.NNFactory
import com.github.neuralnetworks.calculation.neuronfunctions.AparapiTanh
import com.github.neuralnetworks.calculation.neuronfunctions.AparapiSoftReLU
import com.github.neuralnetworks.calculation.neuronfunctions.AparapiReLU
import com.github.neuralnetworks.training.TrainerFactory
import com.github.neuralnetworks.training.random.MersenneTwisterRandomInitializer
import com.github.neuralnetworks.training.random.NNRandomInitializer
import com.github.neuralnetworks.input.MultipleNeuronsOutputError
import com.github.neuralnetworks.test.SimpleInputProvider
import com.github.neuralnetworks.training.TrainingInputProvider
import views.html.helper.input
import com.github.neuralnetworks.training.events.MiniBatchFinishedEvent
import com.github.neuralnetworks.training.backpropagation.BackPropagationAutoencoder
import com.github.neuralnetworks.util.UniqueList
import com.github.neuralnetworks.architecture.Layer
import com.github.neuralnetworks.training.Trainer
import com.github.neuralnetworks.calculation.ValuesProvider
import com.github.neuralnetworks.training.TrainingInputData
import com.github.neuralnetworks.architecture.FullyConnected

/*class BackPropagation {
    protected layer           output_nodes;
        protected layer           hidden_nodes;
        protected int             input_size;
        protected int             output_size;
        protected int             hidden_size;
    private   double          output[];
    private   double          sgm(final double x)   { return (1/(1+Math.exp(-x)));}
    
    public BackPropagation(final int input_size, final int hidden_size, final int output_size) throws Exception{
        if (input_size==0 || hidden_size==0 || output_size==0)
            throw new Exception("Bad network parameters!");
        this.input_size = input_size;
        this.hidden_size = hidden_size;
        this.output_size = output_size;
        this.hidden_nodes = new layer(hidden_size, input_size);
        this.output_nodes = new layer(output_size, hidden_size);
        this.output = new double[output_size];
    }
    public synchronized double train(final double input[], final double desired[], final double eta, final double alpha) throws Exception {
        if (input.length!=input_size || desired.length!=output_size){
            throw new Exception("Bad training parameters!");
        }
        double output_weight_delta[]=new double[output_size], hidden_weight_delta[]=new double[hidden_size];
        double error, sum, mse;
        int i, j;
        try {
            output = run(input);
        }catch(Exception e){ throw e; }
        for (i=0, mse=0; i<output_size; i++){
            output_weight_delta[i] = (error=(desired[i]-output[i]))*(output[i])*(1-output[i]);
            mse += Math.pow(error,2);
        }
        for (i=0; i<hidden_size; i++) {
            for (j=0, sum=0; j<output_size; j++){
                sum += output_weight_delta[j]*(output_nodes.units[j].weights[i]);
            }
            hidden_weight_delta[i] = sum*(hidden_nodes.units[i].output)*(1-(hidden_nodes.units[i].output));
        }
        for (i=0; i<output_size; i++){
            output_nodes.units[i].bdelta = (alpha * output_nodes.units[i].bdelta) + (eta * output_weight_delta[i]);
            output_nodes.units[i].bias += output_nodes.units[i].bdelta;
            for (j=0; j<hidden_size; j++){
                output_nodes.units[i].delta[j]  = (alpha * (output_nodes.units[i].delta[j])) + (eta * output_weight_delta[i] * (hidden_nodes.units[j].output));
                output_nodes.units[i].weights[j] += output_nodes.units[i].delta[j];
            }
        }
        for (i=0; i<hidden_size; i++){
            hidden_nodes.units[i].bdelta = (alpha * hidden_nodes.units[i].bdelta) + (eta * hidden_weight_delta[i]);
            hidden_nodes.units[i].bias += hidden_nodes.units[i].bdelta;
            for (j=0; j<input_size; j++){
                hidden_nodes.units[i].delta[j] = (alpha * (hidden_nodes.units[i].delta[j])) + (eta * hidden_weight_delta[i] * input[j]);
                hidden_nodes.units[i].weights[j] += hidden_nodes.units[i].delta[j];
            }
        }
        return .5*mse;
    }
    public synchronized double[] run (final double input[]) throws Exception{
        if (input.length!=input_size)
            throw new Exception("Bad run parameters!");
        int i, j; double sum, result[] = new double[output_size];
        for (i=0; i<hidden_size; i++) {
            for (j=0, sum=hidden_nodes.units[i].bias; j<input_size; j++){
                sum += ((hidden_nodes.units[i].weights[j]) * (input[j]));
            }
            hidden_nodes.units[i].output = sgm(sum);
        }
        for (i=0; i<output_size; i++) {
            for (j=0, sum=output_nodes.units[i].bias; j<hidden_size; j++){
                sum += ((output_nodes.units[i].weights[j]) * (hidden_nodes.units[j].output));
            }
            output_nodes.units[i].output = sgm(sum);
            result[i] = sgm(sum);
        }
        return result;
    }
    public synchronized double[] runUnit (final double input[], int unit) throws Exception{
        if (input.length!=input_size || unit >= hidden_size || unit<0)
            throw new Exception("Bad run parameters!");
        int i; double sum, result[] = new double[output_size];
        for (i=0, sum=hidden_nodes.units[unit].bias; i<input_size; i++){
            sum += ((hidden_nodes.units[unit].weights[i]) * (input[i]));
        }
        double hidden_unit_output=sgm(sum);
        for (i=0; i<output_size; i++) {
            sum=output_nodes.units[i].bias+((output_nodes.units[i].weights[unit]) * (hidden_unit_output));
            output_nodes.units[i].output = sgm(sum);
            result[i] = sgm(sum);
        }
        return result;
    }
    public synchronized double[] getHiddenLayer(){
        double k[] = new double[hidden_size];
        for (int i=0; i<hidden_size; i++){
            k[i] = hidden_nodes.units[i].output;
        }
        return k;
    }
    protected class layer {
        protected int size, input_size;
        protected neuron units[];
        public layer(final int size, final int input_size){
            this.size = size;
            this.input_size = input_size;
            units = new neuron[size];
            for (int i=0; i<size; i++){
                units[i] = new neuron(input_size);
            }
        }
        protected class neuron{
            double weights[], delta[], output, bias, bdelta;
            public neuron(final int input_size){
                bias = Math.random() * (Math.random() > .5 ? 1 : -1);
                weights = new double[input_size];
                delta = new double[input_size];
                for (int i=0; i<input_size; i++){
                    weights[i] = Math.random() * (Math.random() > .5 ? 1 : -1);
                    delta[i] = 0;
                }
            }
        }
    }
}*/


class AutoEncoder(activation: String, visibleCount: Int, hiddenCount: Int, addBias: Boolean) extends BaseModel {
    private val ae: Autoencoder = activation match {
        case "relu"     => NNFactory.autoencoderReLU(visibleCount, hiddenCount, addBias, new AparapiReLU)
        case "softrelu" => NNFactory.autoencoderSoftReLU(visibleCount, hiddenCount, addBias, new AparapiSoftReLU)
        case "tanh"     => NNFactory.autoencoderTanh(visibleCount, hiddenCount, addBias, new AparapiTanh)
        case _          => NNFactory.autoencoderSigmoid(visibleCount, hiddenCount, addBias)
    }
    var backprop: Trainer[Autoencoder] = null

    def train(data: List[List[Float]], miniBatchSize: Int, learningRate: Float, momentum: Float,
              l1weightDecay: Float, l2weightDecay: Float, corruptionRate: Float) = {
        // Make proper data
        val inputData: TrainingInputProvider = new SimpleInputProvider(data.map(_.toArray).toArray, null, data.size, miniBatchSize)

        // Define learning algorithm
        backprop = TrainerFactory.backPropagationAutoencoder(
            ae, inputData, inputData,
            new MultipleNeuronsOutputError(),
            new NNRandomInitializer(new MersenneTwisterRandomInitializer(-0.01f, 0.01f)),
            learningRate, momentum, l1weightDecay, l2weightDecay, corruptionRate)

        // Train it
        backprop.train()
    }

    // TODO: Get the actual representation
    def getRepresentation(data: List[Float]) = {
        // Make proper input
        backprop.test
        val inputData: TrainingInputProvider = new SimpleInputProvider(Array(data.toArray), null, data.size, 1)
        // Get the network
        val n = backprop.getNeuralNetwork()

        inputData.reset()

        val calculatedLayers: java.util.Set[Layer] = new UniqueList[Layer]()
        val results = new ValuesProvider()
        var input: TrainingInputData = null

        // TODO: Is this needed?
        if (backprop.getOutputError() != null) backprop.getOutputError().reset()

        input = inputData.getNextInput()
        while (input != null) {
            calculatedLayers.clear()
            calculatedLayers.add(n.getInputLayer())
            results.addValues(n.getInputLayer(), input.getInput())
            n.getLayerCalculator().calculate(n, n.getOutputLayer(), calculatedLayers, results)

            // TODO: Is this needed?
            if (backprop.getOutputError() != null)
                backprop.getOutputError().addItem(results.getValues(n.getOutputLayer()), input.getTarget())
            
            input = inputData.getNextInput()
        }
        
        // Get the weights of the hidden layer
        n.getHiddenLayer.getConnections.get(0).asInstanceOf[FullyConnected].getConnectionGraph.getElements
    }

    override def serialize(filename: String): Unit = {

    }

    override def deserialize(filename: String): Unit = {

    }
}