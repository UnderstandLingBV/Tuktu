Example Configurations in Tuktu
=========

All example configuration files given below require Tuktu be running on a server where the *configs* folder is also present. To execute an example configuration file, store it inside the *configs* folder with a descriptive name and the extension `.json`. Run Tuktu by invoking executing the run script and navigate to [http://localhost:9000](http://localhost:9000) and click on the *Start Job* link to run the job defined in the configuration file.

Note that for demonstration purposes, most examples make use of trivial, batch-oriented dummy generators. Examples are shown in code-blocks inside running articles that explain functionality.

Not all generators and processors of Tuktu are presented in the examples. Each module has its own readme file that outlines all the generators and processors that it contains.

# A First Example 
Let's start of by creating some simple example configuration files that do not serve any practical purpose, but merely give a minimal working example to start using Tuktu with.

The first example we will try out simply generates a fixed set of data values and prints them to console.

    {
	    "generators": [
	        {
	            "name": "tuktu.generators.ListGenerator",
	            "result": "num",
	            "config": {
	                "values": [
	                    "a", "b", "c"
	                ]
	            },
	            "next": [
	                "debug"
	            ]
	        }
	    ],
		"processors": [
	        {
	            "id": "debug",
	            "name": "tuktu.processors.ConsoleWriterProcessor",
	            "result": "",
	            "config": {},
	            "next": []
	        }
	    ]
	}

Store this file in the *configs* folder in the root of Tuktu (create the folder if it does not exist yet) and name it *example.json*. Then open [http://localhost:9000](http://localhost:9000) and go to *Start Job* to run the *example* job we just created. You should see the values *a, b, c* being printed on the console Tuktu runs in.

Congratulations, you have now created your first Tuktu job!

Continue reading on to the next sections to see what other functionalities Tuktu has to offer.

# Basic Functionality
When dealing with data, transforming and manipulating any form of data is a basic fundament. Tuktu comes packed with numerous data altering functionalities that are common in ETL processes.

To show a great number of processing tasks in one go, following the ideology that deep-diving is a good didactic method, we advance by showing a lengthy, yet realistic example below. The steps that are taken in this job are described in detail subsequently to explain what is going on.

 

## Meta Processors
Tuktu typically follows a transactional flow within a single actor on a single node. There are however, numerous processors that break this default data flow.

### Distributed Functionality
As a special kind of meta-processing, Tuktu has built-in functionalities for distributed computing.

# Social Functionality

# CSV Functionality

# NoSQL Integration

# NLP Functionality
Tuktu contains numerous processors that implement some Natural Language Processing (NLP) algorithms for analyzing human-written texts.
