### tuktu.web.processors.FreeGeoIPProcessor
Searches the geolocation of IP addresses using an instance of the freegeoip.net web service. See http://freegeoip.net/ for details about the service and its limitations.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **ip** *(type: string)* `[Required]`
    - The IP address to lookup.

    * **geoipurl** *(type: string)* `[Optional, default = "http://freegeoip.net"]`
    - The URL of the Free Geo IP service instance to call.

    * **format** *(type: string)* `[Optional, default = "json"]`
    - The format (json, csv or xml) in which the geolocation data should be returned (default is json).

