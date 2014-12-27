# Social Extension of Tuktu
This module contains some basic social functions for usage in Tuktu.

#Generators in this module
## tuktu.social.generators.TwitterGenerator
Uses the [Twitter filter stream](https://dev.twitter.com/streaming/reference/post/statuses/filter "Twitter filter stream") to obtain tweets and stream them into Tuktu.

### Configuration
    "credentials": {
		"consumer_key": "abc",
		"consumer_secret": "def",
		"access_token": "123",
		"access_token_secret": "456",
	},
	"filters": {
		"keywords": ["word1", "word2"],
		"userids": ["userid1", "userid2"],
		"geo": [[12.3, 45.6], [789.0, 0.12]]
	}

#### `credentials` [Obligated]
Contains your application's Twitter credentials, to be found and generated on the [Twitter application overview page](https://apps.twitter.com/).

#### `filters` [At least one must be present]
To tell Twitter what tweets to return, the `filters` are used. Keywords are natural language words that are contained in a tweet returned by Twitter. The user IDs are the Twitter IDs for users that are to be followed. The geo field contains geolocation bounding boxes. For more information, see [the Twitter documentation](https://dev.twitter.com/streaming/reference/post/statuses/filter). Note that at least one filter must be present.

## tuktu.social.generators.FacebookGenerator

# Processors in this module

## tuktu.social.processors.TwitterTaggerProcessor

## tuktu.social.processors.FacebookTaggerProcessor

## tuktu.social.processors.FacebookRESTProcessor