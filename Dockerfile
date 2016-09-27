FROM alpine:latest

MAINTAINER Pim Witlox

# our versions of the tools
ENV SBT_VERSION 0.13.12
ENV SCALA_VERSION 2.11.8
ENV ACTIVATOR_VERSION 1.3.10
ENV TUKTU_VERSION 1.2

# general stuff
USER root
RUN apk update && apk upgrade
WORKDIR /tmp

# install openjdk8
RUN apk add openjdk8
ENV JAVA_HOME /usr/lib/jvm/java-1.8-openjdk 
ENV PATH $JAVA_HOME/bin:$PATH

# install sbt  
RUN apk add curl
ENV SBT_HOME /usr/local/sbt
RUN curl -sL "http://dl.bintray.com/sbt/native-packages/sbt/$SBT_VERSION/sbt-$SBT_VERSION.tgz" | gunzip | tar -x -C /usr/local && echo -ne "- with sbt $SBT_VERSION\n" >> /root/.built
ENV PATH $SBT_HOME/bin:$PATH

# install scala  
ENV SCALA_HOME /usr/share/scala
RUN apk add --no-cache --virtual=.build-dependencies wget ca-certificates && apk add --no-cache bash && cd "/tmp" && wget "https://downloads.typesafe.com/scala/$SCALA_VERSION/scala-$SCALA_VERSION.tgz" && \
    tar xzf "scala-$SCALA_VERSION.tgz" && mkdir "$SCALA_HOME" && rm "/tmp/scala-$SCALA_VERSION/bin/"*.bat && mv "/tmp/scala-$SCALA_VERSION/bin" "/tmp/scala-$SCALA_VERSION/lib" "$SCALA_HOME" && \
    ln -s "$SCALA_HOME/bin/"* "/usr/bin/" && apk del .build-dependencies
ENV PATH $SCALA_HOME/bin:$PATH

# download and install activator
RUN wget "http://downloads.typesafe.com/typesafe-activator/$ACTIVATOR_VERSION/typesafe-activator-$ACTIVATOR_VERSION-minimal.zip"
RUN unzip typesafe-activator-$ACTIVATOR_VERSION-minimal.zip
RUN mv activator-$ACTIVATOR_VERSION-minimal /usr/share/activator
ENV PATH /usr/share/activator/bin:$PATH

# AKKA communication and Tuktu UI
EXPOSE 2552
EXPOSE 9000

# download our application distribution, extract it and move to the correct spot
RUN wget "http://dl.bintray.com/witlox/tuktu/tuktu-$TUKTU_VERSION.zip"
RUN unzip tuktu-$TUKTU_VERSION.zip
RUN mv tuktu-$TUKTU_VERSION /usr/share/tuktu-$TUKTU_VERSION

# change working dir to packaged version
WORKDIR /usr/share/tuktu-$TUKTU_VERSION

# clean up
RUN rm -rf /tmp/* /var/tmp/* /var/cache/apk/*

# and.. go!
RUN chmod +x run.sh
ENTRYPOINT ["/bin/sh"]
CMD ["./run.sh""]
