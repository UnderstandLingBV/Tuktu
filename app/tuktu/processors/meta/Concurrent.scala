package tuktu.processors.meta

import tuktu.api.BaseProcessor

/**
 * Dummy processor that turns any subsequent flow into a true parallel flow (done by the Dispatcher, no implementation needed)
 */
abstract class ConcurrentProcessor(resultName: String) extends BaseProcessor(resultName) {
    
}