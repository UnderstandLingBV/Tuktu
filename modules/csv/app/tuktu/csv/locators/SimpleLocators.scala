package tuktu.csv.locators

import play.api.libs.json.JsValue

class Column(params: JsValue) extends BaseLocator(params) {
    // Get the column offset
    val columnOffset = (params \ "offset").as[Int]
    val rowStart = (params \ "row_start").as[Int]
    val rowEnd = (params \ "row_end").asOpt[Int]
    var previousValue: String = null
    
    override def getFromCsvRow: (List[String], Int, Int) => String = (row, rowIndex, colIndex) => {
        if (rowIndex >= rowStart && colIndex >= columnOffset) {
            rowEnd match {
                // Get the column's value, ignore cell offsets
                case Some(end) => {
                    if (rowIndex < end) {
                        // See if we actually have a proper value
                        val value = row(columnOffset)
                        if (value == null || value.isEmpty) previousValue
                        else {
                            previousValue = row(columnOffset)
                            value
                        }
                    } else null
                }
                case None => {
                    // See if we actually have a proper value
                    val value = row(columnOffset)
                    if (value == null || value.isEmpty) previousValue
                    else {
                        previousValue = row(columnOffset)
                        value
                    }
                }
            }
        } else null
    }
}

class Row(params: JsValue) extends BaseLocator(params) {
    // Get the row offset
    val rowOffset = (params \ "offset").as[Int]
    var previousValue: String = null
    
    // List containing the values
    var values = collection.mutable.ListBuffer[String]()
    
    override def getFromCsvRow: (List[String], Int, Int) => String = (row, rowIndex, colIndex) => {
        // See if we need to get this one
        if (rowIndex == rowOffset) {
            // Repeat missing values, if any
            if (colIndex >= row.size)
                values.insert(colIndex, previousValue)
            else if (row(colIndex) == null || row(colIndex).isEmpty)
                values.insert(colIndex, previousValue)
            else {
                values.insert(colIndex, row(colIndex))
                previousValue = row(colIndex)
            }
        }
        
        // Get the value for this cell
        if (values.nonEmpty) {
            // See if this index is out of bounds (repeat previous value)
            if (colIndex > values.size) previousValue
            else values(colIndex)
        }
        else null
    }
}

class CellRangeSplitter(params: JsValue) extends BaseLocator(params) {
    // Get the cell offset
    val rowOffset = (params \ "row").as[Int]
    val colOffset = (params \ "col").as[Int]
    val separator = (params \ "split").as[String]
    val step = (params \ "step").asOpt[Int].getOrElse(1)
    
    // List containing the range
    var values = collection.mutable.ListBuffer[String]()
    
    override def getFromCsvRow: (List[String], Int, Int) => String = (row, rowIndex, colIndex) => {
        if (rowIndex == rowOffset && colIndex == colOffset) {
            // Split cell based on separator
            val cellValue = row(colIndex)
            if (cellValue.contains(separator)) {
                val split = cellValue.split(separator)
                val start = split(0).toInt
                val end = split(1).toInt
                
                // Generate range
                if (start > end) values.insertAll(0, (start to end by step).map(n => n.toString))
                else values.insertAll(0, (end to start by (-1 * step)).map(n => n.toString))
            }
            else values.insert(0, cellValue)
        }
        
        // Return the value
        if (values.nonEmpty)
            values(colIndex % values.size)
        else null
    }
}