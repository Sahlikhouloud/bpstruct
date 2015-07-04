BPStruct is a tool for transforming unstructured processes/programs/service compositions (models of concurrency) into (well-)structured ones. A model is _(well-)structured_, if for every node with multiple outgoing arcs (a _split_) there is a corresponding node with multiple incoming arcs (a _join_), and vice versa, such that the fragment of the model between the split and the join forms a single-entry-single-exit (SESE) process component; otherwise the model is _unstructured_. The transformation preserves concurrency in resulting structured models.

## Command Line Tool ##

```
java -jar bpstruct-x.y.z.jar -odir output model.json
```

`model.json` contains an input model. The serialization format and the structural constraints imposed on the model contained in `model.json` are described [here](SerializationFormat.md). More on the usage of the command line tool can be found [here](CommandLineTool.md).

## REST API ##

BPStruct can be accessed using REST API. A publicly available version of the service is located at: [http://141.89.225.233/bpstruct/rest](http://141.89.225.233/)

## Live DEMO ##

You can try BPStruct online! Signavio Academic Initiative (AI) integrates BPStruct by using REST API. For the detailed instructions on how to use BPStruct in Signavio AI please read [here](SignavioAI.md). Please download command line tool if you want to use the latest BPStruct version (see above).

## Examples ##

![http://bpstruct.googlecode.com/files/bpstruct.png](http://bpstruct.googlecode.com/files/bpstruct.png)

## Presentations ##
  * <a href='http://www.slideshare.net/ArtemPolyvyanyy/structuring-acyclic-process-models' title='Structuring Acyclic Process Models'>Structuring Acyclic Process Models</a>
  * <a href='http://www.slideshare.net/bpmn2010/bpmn2010-dumas-5557965' title='Unraveling Unstructured Process Models'>Unraveling Unstructured Process Models</a>

## Publications ##
  * Artem Polyvyanyy and Christoph Bussler. _The Structured Phase of Concurrency_. In Bubenko, J., Krogstie, J., Pastor, O., Pernici, B., Rolland, C., & Sølvberg, A. (Eds.) Seminal Contributions to Information Systems Engineering : 25 Years of CAiSE. Springer, 2013 [postprint](http://eprints.qut.edu.au/59708/)
  * Artem Polyvyanyy, Luciano García-Bañuelos, Dirk Fahland, and Mathias Weske. _Maximal Structuring of Acyclic Process Models_. The Computer Journal (CJ), 2012, Oxford University Press [preprint](http://eprints.qut.edu.au/57406/) [official](http://dx.doi.org/10.1093/comjnl/bxs126)
  * Artem Polyvyanyy. _Structuring Process Models_. PhD dissertation. University of Potsdam, Germany, January 2012 [link](http://nbn-resolving.de/urn:nbn:de:kobv:517-opus-59024)
  * Artem Polyvyanyy, Luciano García-Bañuelos, and Marlon Dumas. _Structuring Acyclic Process Models_. Information Systems (IS), 2011
  * Artem Polyvyanyy, Luciano García-Bañuelos, Dirk Fahland, and Mathias Weske. _Maximal Structuring of Acyclic Process Models_. The Computing Research Repository (CoRR), August 2011
  * Marlon Dumas, Luciano García-Bañuelos, and Artem Polyvyanyy. _Unraveling Unstructured Process Models_. Proceedings of the 2nd International Workshop on BPMN (BPMN). Potsdam, Germany, October 2010
  * Artem Polyvyanyy, Luciano García-Bañuelos, and Marlon Dumas. _Structuring Acyclic Process Models_. Proceedings of the 8th International Conference on Business Process Management (BPM). Hoboken, NJ, US, September 2010
  * Artem Polyvyanyy, Jussi Vanhatalo, and Hagen Voelzer. _Simplified Computation and Generalization of the Refined Process Structure Tree_. Proceedings of the 7th International Workshop on Web Services and Formal Methods (WS-FM). Hoboken, NJ, US, September 2010

## Friend projects ##
  * jbpt - Business Process Technologies 4 Java. http://code.google.com/p/jbpt/
  * Signavio Academic Initiative integrates BPStruct functionality using REST API. http://www.signavio.com/en/academic.html
  * oryx-editor - Web-based Graphical Business Process Editor. http://code.google.com/p/oryx-editor/