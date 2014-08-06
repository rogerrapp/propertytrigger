propertytrigger
===============

Jenkins plugin which monitors selected properties in a property file checked into Perforce.

The plugin triggers a build if selected properties have changed in a file.
Specify properties like this:

//the_perforce_path/exmple.properties;the_key
...

E.g
A property file //depot/theStream/config/lab.properties:

foo=4711
bar=666

Subscribe on the key 'bar':

//depot/theStream/config/lab.properties;bar


This trigger communicates with Perforce through the p4java api.
http://www.perforce.com/perforce/doc.current/manuals/p4java/
