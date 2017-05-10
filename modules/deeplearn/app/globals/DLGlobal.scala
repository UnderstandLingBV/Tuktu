package globals

import tuktu.api.TuktuGlobal
import play.api.Application
import play.api.Play
import tuktu.deeplearn.models.image.VGG16
import tuktu.deeplearn.models.image.InceptionV3

class DLGlobal() extends TuktuGlobal() {
    override def onStart(app: Application) = {
        if (Play.current.configuration.getBoolean("tuktu.dl.vgg16.load_on_start").getOrElse(false)) VGG16.load
        if (Play.current.configuration.getBoolean("tuktu.dl.inception.load_on_start").getOrElse(false)) InceptionV3.load
    }
}