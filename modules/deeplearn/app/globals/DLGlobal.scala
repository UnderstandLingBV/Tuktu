package globals

import tuktu.api.TuktuGlobal
import play.api.Application
import play.api.Play
import tuktu.deeplearn.models.image.VGG16

class DLGlobal() extends TuktuGlobal() {
    override def onStart(app: Application) = {
        if (Play.current.configuration.getBoolean("tuktu.dl.vgg16.load_on_start").getOrElse(true)) VGG16.load
    }
}