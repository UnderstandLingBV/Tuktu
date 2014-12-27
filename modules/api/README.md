# Tuktu API
The API of Tuktu is a stand-alone module that can be used to extend the functionailities of Tuktu. It allows any developer to just import this module and use its interfaces to continue adding functionality to Tuktu.

Tuktu's API consist of the basic elements that drive the communication of Tuktu and the processing of data.

# Packets
Data flowing through Tuktu is wrapped in packets. The default packet used to transport data from one generator/processor to another processor is caleld a `DataPacket`. The `DataPacket` class contains a member called `data` of type  `List[Map[String, Any]]`. This reflects the data as a list of mappings (when buffering, multiple maps are added to one single list) from a named String to any arbitrary value.

Next to the `DataPacket`s, Tuktu also contains many packets for control flow. The `InitPacket`, `StopPacket` and `ResponsePacket` are used by Tuktu internally to signal start, end or response of data flows.

Tuktu contains a monitoring system that is sent specifically typed packets for monitoring purposes. These are the `MonitorPacket`s.

# Base Processors and Generators
The core of Tuktu consists of generators and processors. Any generator or processor that is invoked by Tuktu should be an extension of the `BaseGenerator` or `BaseProcessor` classes (or rather, should adhere to their signatures and method signatures).

A `BaseProcessor` is a [Play! Enumeratee](https://www.playframework.com/documentation/2.3.x/Enumeratees "Play! Enumeratee"), taking a `DataPacket` and transforming it into another `DataPacket`, reflecting the fact that processors take data and manipulate it rather than generate it.

A `BaseGenerator` is an [Akka](http://www.akka.io "Akka") actor that uses broadcasting to enumerate data into a channel. Being an actor, a `BaseGenerator` must always implement the `receive` function. By default, Tuktu's dispatcher sends a `JsValue` configuration to a generator once, which is whereafter the generator should initiate its own date generation. Note that a generator can broadcast data into multiple processors.

For generators, two default extensions to the `BaseGenerator` exist, an asynchronous one and a synchronous one. The asynchronous one sends the generated data to all its child-processors. The synchronous generator does the same, but returns the result for the very first chain of processors it finds. 