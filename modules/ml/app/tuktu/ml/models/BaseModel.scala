package tuktu.ml.models

abstract class BaseModel() extends java.io.Serializable {
    def serialize(filename: String): Unit = ???
    def deserialize(filename: String): Unit = ???
} 