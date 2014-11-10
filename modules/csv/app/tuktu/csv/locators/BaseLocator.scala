package tuktu.csv.locators

import play.api.libs.json.JsValue

abstract class BaseLocator(params: JsValue) {
    def getFromCsvRow(): (List[String], Int, Int) => String = ???
}