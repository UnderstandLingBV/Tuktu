package tuktu.nosql.util

import reactivemongo.bson.BSONDocumentReader
import reactivemongo.bson.BSONDocumentWriter
import reactivemongo.bson.BSONDocument

object BSONMap {
    implicit def MapReader[V](implicit vr: BSONDocumentReader[V]): BSONDocumentReader[Map[String, V]] = new BSONDocumentReader[Map[String, V]] {
        def read(bson: BSONDocument): Map[String, V] = {
            val elements = bson.elements.map { tuple =>
                // assume that all values in the document are BSONDocuments
                tuple._1 -> vr.read(tuple._2.seeAsTry[BSONDocument].get)
            }
            elements.toMap
        }
    }

    implicit def MapWriter[V](implicit vw: BSONDocumentWriter[V]): BSONDocumentWriter[Map[String, V]] = new BSONDocumentWriter[Map[String, V]] {
        def write(map: Map[String, V]): BSONDocument = {
            val elements = map.toStream.map { tuple =>
                tuple._1 -> vw.write(tuple._2)
            }
            BSONDocument(elements)
        }
    }
}