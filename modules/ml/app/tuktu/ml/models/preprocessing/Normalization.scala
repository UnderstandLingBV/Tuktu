package tuktu.ml.models.preprocessing

import tuktu.ml.models.BaseModel
import java.io.FileOutputStream
import java.io.ObjectOutputStream
import java.io.FileInputStream
import java.io.ObjectInputStream

/**
 * Normalizes data into a specific scale
 */
class Normalization(minScale: Double = 0.0, maxScale: Double = 1.0) extends BaseModel {
    var minMaxes = collection.mutable.Map.empty[String, (Double, Double)]
    
    // Checks if there is a new min/max value for this key
    def addData(key: String, value: Double) = {
        if (!minMaxes.contains(key)) minMaxes += key -> (value, value)
        else {
            val min = if (minMaxes(key)._1 > value) value else minMaxes(key)._1
            val max = if (minMaxes(key)._2 > value) value else minMaxes(key)._2
            minMaxes += key -> (min, max)
        }
    }
    
    def toDouble(k: String, value: Any) = {
        value match {
            case v: Int => v.toDouble
            case v: Short => v.toDouble
            case v: Long => v.toDouble
            case v: Byte => v.toDouble
            case v: Double => v
            case v: BigDecimal => v.toDouble
            case v: String => v.toDouble
            case v: Any => v.toString.toDouble
        }
    }
    
    // Normalizes a value
    def normalize(key: String, value: Double) = {
        if (minMaxes.contains(key)) {
            (value - minMaxes(key)._1) / (minMaxes(key)._2 - minMaxes(key)._1) * (maxScale - minScale) + minScale
        } else value
    }
    
    override def serialize(filename: String) = {
        val oos = new ObjectOutputStream(new FileOutputStream(filename))
        oos.writeObject(minMaxes)
        oos.close
    }
    
    override def deserialize(filename: String) = {
        val ois = new ObjectInputStream(new FileInputStream(filename))
        minMaxes = ois.readObject.asInstanceOf[collection.mutable.Map[String, (Double, Double)]]
        ois.close
    }
}