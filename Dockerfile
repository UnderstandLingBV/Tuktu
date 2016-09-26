FROM openjdk:8

MAINTAINER Pim Witlox

ENV ACTIVATOR_VERSION 1.3.10
ENV SCALA_VERSION 2.11.8
ENV SBT_VERSION 0.13.9
ENV TUKTU_VERSION 1.2

# install sbt  
RUN wget https://dl.bintray.com/sbt/debian/sbt-$SBT_VERSION.deb  
RUN dpkg -i sbt-$SBT_VERSION.deb  

# install scala  
RUN wget https://downloads.typesafe.com/scala/$SCALA_VERSION/scala-$SCALA_VERSION.deb  
RUN dpkg -i scala-$SCALA_VERSION.deb  

# Download and install Activator
RUN wget --output-document /opt/typesafe-activator-$ACTIVATOR_VERSION-minimal.zip http://downloads.typesafe.com/typesafe-activator/$ACTIVATOR_VERSION/typesafe-activator-$ACTIVATOR_VERSION-minimal.zip
RUN unzip /opt/typesafe-activator-$ACTIVATOR_VERSION-minimal.zip -d /opt
RUN rm -f /opt/typesafe-activator-$ACTIVATOR_VERSION-minimal.zip
RUN mv /opt/activator-$ACTIVATOR_VERSION-minimal /opt/activator

# Add activator to path
ENV PATH /opt/activator/bin:$PATH

# Expose port for AKKA communication
EXPOSE 2552

# Expose port for Tuktu UI
EXPOSE 9000

# Build our application distribution
ADD . SRC
WORKDIR SRC
RUN activator dist

# Extract our distribtion
RUN unzip target/universal/tuktu-$TUKTU_VERSION.zip
RUN mv tuktu-$TUKTU_VERSION /opt/tuktu-$TUKTU_VERSION

# Change working dir to packaged version
WORKDIR /opt/tuktu-$TUKTU_VERSION

# and.. go!
CMD ["run.sh"]
