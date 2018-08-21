#First version gRPC geometry service based off of the ESRI geometry-api-java library.

## Why gRPC and protocol buffers
### Protocol Buffers
They're basically like a binary JSON message. It's platform and language neutral (Python, C++, Java, Go, Node.js). Google provides libraries for each language for auto-generating library code to use the service defined in the proto file. Serializing and deserializing a smaller message is going to improve throughput.

Quote from google docs:
>Protocol buffers are a flexible, efficient, automated mechanism for serializing structured data – think XML, but smaller, faster, and simpler. You define how you want your data to be structured once, then you can use special generated source code to easily write and read your structured data to and from a variety of data streams and using a variety of languages. You can even update your data structure without breaking deployed programs that are compiled against the "old" format.

###HTTP2 vs HTTP1
One TCP connection with true multiplexing. From [HTTP/2 FAQ](https://http2.github.io/faq/#why-just-one-tcp-connection):
> One application opening so many connections simultaneously breaks a lot of the assumptions that TCP was built upon; since each connection will start a flood of data in the response, there’s a real risk that buffers in the intervening network will overflow, causing a congestion event and retransmits.

http://www.http2demo.io/

## Building for developement

### Building Protobuf
To compile the protobuf code you'll need to follow the below instructions
https://github.com/grpc/grpc-java/blob/master/COMPILING.md#build-protobuf

On a mac you may need to install the following libraries for the `./autogen.sh` line:
```bash
brew reinstall libtool
brew reinstall mozjpeg
brew reinstall autoconf
brew reinstall automake
brew install ant
```


### Building project for Java
To build the library call (if you don't have gradle, `brew install gradle`, if you own a linux/windoze, godspeed):
```bash
gradle build
gradle build install
```
from within repo directory.

## Building Proj.4 for with JNI
### Requirements

Beyond the ones already put by Proj.4, you need:
- JSE 1.5+, the Java standard development kit version 1.5 or above
- Ant, to run the build
### Mac 
Set `JAVA_HOME`
```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 1.8)"
```

build Proj.4 with jni flags
```bash
CFLAGS=-I$JAVA_HOME/include/darwin ./configure --with-jni=$JAVA_HOME/include
make
make install
cd jniwrap
ant
cp ./jniwrap/libs/jproj.jar /usr/local/lib/
```

### Debian
Set `JAVA_HOME` (https://serverfault.com/a/276221/390998)
```bash
JAVA_HOME=$(readlink -f /usr/bin/javac | sed "s:/bin/javac::")
```

build Proj.4 with jni flags
```bash
CFLAGS=-I$JAVA_HOME/include/linux ./configure --with-jni=$JAVA_HOME/include
make
make install
cd jniwrap
ant
```


For Intellij set the `java.library.path`, [this StackOverflow post](http://stackoverflow.com/a/19311972/445372) describes debugging and building with it. And in the case of Gradle, I don't know where to set the following:
```bash
-Djava.library.path=<path to the libproj, if needed>
```
for example on my mac, in Intellij, I have set the `VM Options` in my test configuration to:
```bash
-ea  -Djava.library.path=/usr/local/lib/
```

## Docker

build the library and then build the docker image:
```bash
gradle build
gradle build install
docker build --no-cache -t geometry-service .
```

Run a container on a local dev machine:
```bash
docker run -p 8980:8980 -it --name=temp-c us.gcr.io/echoparklabs/geometry-service-java
```

to test your local java client build against the docker container (after you've run `gradle build` on local dev machine), you'll run:
```bash
./build/install/geometry-service/bin/geometry-operators-client
```

running a python sample will also test the docker container. To build it you will need to follow the instructions in the geometry-client-python directory's README.md:
```bash
python -m pip install --upgrade pip
pip install grpc
pip install shapely
python -m pip install grpcio
```

to run the python sample:
```bash
python geometry-client-python/geometry_client/sample.py
```

## API Style
### Chaining :
 ```java
ServiceGeometry serviceGeometry = ServiceGeometry.newBuilder().setGeometryEncodingType("wkb").setGeometryBinary(ByteString.copyFrom(op.execute(0, polyline, null))).build();
    OperatorRequest serviceConvexOp = OperatorRequest
            .newBuilder()
            .setLeftGeometry(serviceGeometry)
            .setOperatorType(Operator.Type.ConvexHull.toString())
            .build();

    OperatorRequest serviceOp = OperatorRequest.newBuilder()
            .setLeftCursor(serviceConvexOp)
            .addBufferDistances(1)
            .setOperatorType(Operator.Type.Buffer.toString())
            .build();
```