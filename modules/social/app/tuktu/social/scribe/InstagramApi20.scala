package tuktu.social.scribe

import com.github.scribejava.apis.KaixinApi20
import com.github.scribejava.core.builder.api.DefaultApi20
import com.github.scribejava.core.builder.api.OAuth2SignatureType
import com.github.scribejava.core.model.Verb

object InstagramApi20 {
    val INSTANCE = new InstagramApi20()

    def instance() = INSTANCE
}

class InstagramApi20 extends DefaultApi20 {
    override def getAccessTokenVerb() = Verb.GET
    override def getAccessTokenEndpoint() = "https://api.instagram.com/oauth/access_token"
    override def getAuthorizationBaseUrl() = "https://api.instagram.com/oauth/authorize"
}