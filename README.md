# utonlineserver

Some test UDP server code for implementing multiplayer functionality into Undertale (or other basic GameMaker games). It is not particularly great, but it works well enough for its purpose.

### Building

This code is purely Java with no external dependencies, so building it should be fairly simple.

### Running

After building, place the binaries (such as a JAR file) in a directory that is able to be filled with a config file and log files.

By default it will spin up a server on port 1337, listening for UDP packets from proper clients. This behavior and more can be configured with the (generated) `config.properties` file.

Log files are generated for the main thread and each server. Servers will generate new log files if they have activity after one hour of using a particular log file.
