# Tuktu - Big Data Science Swiss Army Knife

[![Join the chat at https://gitter.im/UnderstandLingBV/Tuktu](https://badges.gitter.im/UnderstandLingBV/Tuktu.svg)](https://gitter.im/UnderstandLingBV/Tuktu?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) [![Build Status](https://travis-ci.org/witlox/Tuktu.svg?branch=master)](https://travis-ci.org/witlox/Tuktu) [![Release](https://img.shields.io/github/release/witlox/Tuktu.svg)](https://github.com/witlox/Tuktu/releases/latest) [![Docker Automated build](https://img.shields.io/docker/automated/witlox/Tuktu.svg?maxAge=2592000)](https://hub.docker.com/r/witlox/tuktu) [![ImageLayers Size](https://img.shields.io/imagelayers/image-size/witlox/Tuktu/latest.svg?maxAge=2592000)](https://imagelayers.io/?images=witlox%2Ftuktu:latest)

Tuktu is created and officially maintained by [UnderstandLing Intellect](http://www.understandling.com).

![UnderstandLing Logo](images/ul.png)

Tuktu is a big data analytics platform that focuses on ease of use. The idea of the platform is that its users can focus on (business) logic rather than dealing with technical implementation details. Tuktu comes with a couple of key characteristics:

- Support for real-time and batch processing
- Synchronous and asynchronous processing
- A visual modeller that allows to create jobs using [drag-and-drop modelling](modules/modeller)
- Tuktu has its own distributed file system that is very easy to use - alternatively, Tuktu integrates seamlessly with HDFS (and local files of course)
  - You can use prefixes like `file://`, `hdfs://`, `s3://` and `tdfs://` to seamlessly switch between file systems. 
- Tuktu also has its own in-memory distributed key-value store for quickly storing and retrieving data. Conceptually, this is close to [Spark](http://spark.apache.org/)'s RDD
- Tuktu has built-in real-time visualization capabilities for a number of pre-defined visuals
- Native support for web-analytics
- Periodic jobs can be scheduled natively from Tuktu
- By default there is no master/slave architecture, so no single point of failure
- Switch seamlessly between distributed and local (even transactional) computation paradigms
- Modular setup, with a number of modules like [Machine Learning](modules/ml) and [Social](modules/social) already worked out
- Automatic generation of documentation using the meta-files in the [modeller](modules/modeller)
- Easy usage and setup: download the zip file and run the startup script (Windows, Mac and Linux supported)

The name comes from the Inuït word *tuktu*, which freely translates to the English word *reindeer* (also known as *[caribou](http://en.wikipedia.org/wiki/Caribou)*).

# Documentation

For documentation on Tuktu, please see [http://www.tuktu.io](http://www.tuktu.io).

# Partners
The following companies or organizations use or actively contribute to Tuktu.

![RISA Logo](https://www.risa-it.nl/wp-content/uploads/2015/08/logo-risa_mob.png)

[RISA IT](https://www.risa-it.nl/) is a partner of the Tuktu platform and is using it in all of its big data science projects.

![Alten logo](http://www.alten.nl/wp-content/themes/alteneurope/images/logo.png)

[Alten](http://www.alten.nl/) is a partner of the Tuktu platform and is using it in all of its big data science projects.

<img src="images/zettadata_logo.png" width="80">

[Zettadata](http://www.zettadata.net/) uses Tuktu as a platform to help clients manage digital assets and harness multiple information streams for data-savvy decision making.

![AnyPinion Logo](http://anypinion.com/assets/images/logo_grey.png)

[AnyPinion](http://anypinion.com/) uses Tuktu for its API and real-time analytics module. AnyPinion has been an adopter of Tuktu from the very beginning.

[University of Maastricht](http://www.maastrichtuniversity.nl/) The University of Maastricht has been using Tuktu as the core platform for performing analytics on open source data. The University of Maastricht has been using Tuktu from the very beginning.
