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

**Scenario**

Consider being requested to analyze a piece of open data. More specifically, you are given information on crime statistics from the London police department. Such an example file is found [data/2015-01-city-of-london-street.csv](data/2015-01-city-of-london-street.csv) and contains information on crime in London stemming from January 2015. This data is actually obtained from the [http://data.police.uk/data/](UK police open data website), but has the CSV file has been crippled a bit. In fact, this CSV file is not valid because same forward slashes have been manually replaced by commas. Note that this is not true in the originally downloaded file, but from the sake of example this has been added. This is a very common scenario where you are given a corrupted piece of data that needs to be analyzed nonetheless.

It is your task to find all reported incidents without a proper *Crime Type* associated (being *Other crime* or *Other theft*) in the locations *On or near Conference/Exhibition Centre*, *On or near Further/Higher Educational Building* and all incidents with no location associated. For the former two, the data has been corrupted by replacing the forward slash with a comma. The following Tuktu job fixes this corruption, filters our all data to maintain only those indicents that are in the right location and that do not have a specific crime type associated with them. The final result is written to a file.

	{
	    "generators": [
	        {
	            "name": "tuktu.generators.LineGenerator",
	            "result": "line",
	            "config": {
	                "filename": "data/2015-01-city-of-london-street.csv"
	            },
	            "next": [
	                "corrector"
	            ]
	        }
	    ],
	    "processors": [
	        {
	            "id": "corrector",
	            "name": "tuktu.processors.ReplaceProcessor",
	            "result": "",
	            "config": {
	                "field": "line",
	                "sources": [
	                    "On or near Conference, Exhibition Centre",
	                    "On or near Further,Higher Educational Building"
	                ],
	                "targets": [
	                    "On or near Conference/Exhibition Centre",
	                    "On or near Further/Higher Educational Building"
	                ]
	            },
	            "next": [
	                "csvConverter"
	            ]
	        },
	        {
	            "id": "csvConverter",
	            "name": "tuktu.csv.processors.CSVReaderProcessor",
	            "result": "",
	            "config": {
	                "field": "line",
	                "remove_original": true,
	                "headers_from_first": true
	            },
	            "next": [
	                "locationFilter"
	            ]
	        },
	        {
	            "id": "locationFilter",
	            "name": "tuktu.processors.InclusionProcessor",
	            "result": "",
	            "config": {
	                "expression": "Location=No Location,Location=On or near Conference/Exhibition Centre,Location=On or near Further/Higher Educational Building",
	                "type": "simple",
	                "and_or": "or"
	            },
	            "next": [
	                "outcomeFilter"
	            ]
	        },
	        {
	            "id": "outcomeFilter",
	            "name": "tuktu.processors.InclusionProcessor",
	            "result": "",
	            "config": {
	                "expression": "Crime type=Other crime,Crime type=Other theft",
	                "type": "simple",
	                "and_or": "or"
	            },
	            "next": [
	                "debug", "writer"
	            ]
	        },
	        {
				"id": "writer",
				"name": "tuktu.csv.processors.CSVWriterProcessor",
				"result": "",
				"config": {
					"file_name": "data/my_first_tuktu_output.csv"
				},
				"next": []
			},
	        {
	            "id": "debug",
	            "name": "tuktu.processors.ConsoleWriterProcessor",
	            "result": "",
	            "config": {},
	            "next": []
	        }
	    ]
	}

**How to run**

To run this Tuktu job, save it to a file in Tuktu's `configs` folder. Make sure you have a `data` folder present in the folder Tuktu is ran from and put the source CSV file in it (this file can be obtained [data/2015-01-city-of-london-street.csv](here)). Run Tuktu by executing the `bin/tuktu(.bat)` script. Navigate to [http://localhost:9000](http://localhost:9000) and go to [http://localhost:9000/startJob](the start job section) to launch the job.

You will most likely not see the job actually run under `Running Jobs` since it will finish very fast, but you should see data being printed to the console that opened when you ran the Tuktu startup script. 

**Step-by-step Explanation**

	"generators": [
	        {
	            "name": "tuktu.generators.LineGenerator",
	            "result": "line",
	            "config": {
	                "filename": "data/2015-01-city-of-london-street.csv"
	            },
	            "next": [
	                "corrector"
	            ]
	        }
	    ]
We first start by defining all generators of our job, in this case just one. The name of the generator is actually a java class name as it is defined in Tuktu. In this case, we use the `tuktu.generators.LineGenerator` because we cannot use a CSV generator since our input data is not valid CSV. Every generator puts its resulting data in a specific field defined by the `result` parameter. Every generator also requires specific configuration to initialize it poperly. In this case, all we need to tell the generator is what file to read. The generator sends its data on to all processors that are listed in the `next` field, in this case just a processor called `corrector`.

	{
        "id": "corrector",
        "name": "tuktu.processors.ReplaceProcessor",
        "result": "",
        "config": {
            "field": "line",
            "sources": [
                "On or near Conference, Exhibition Centre",
                "On or near Further,Higher Educational Building"
            ],
            "targets": [
                "On or near Conference/Exhibition Centre",
                "On or near Further/Higher Educational Building"
            ]
        },
        "next": [
            "csvConverter"
        ]
    }

We next define the list of processors that we require in this job, starting with the `tuktu.processors.ReplaceProcessor`. The goal of this processor is to replace the incorrectly places commas with forward slashes. We do so by specifying the source and target pairs that need to be replaced. Notice how the configuration parameter `field` is used to find the actual data that our generator previously read from a file. Als note how this processor does not use the `resultName` field and hence we can leave it blank. You will find that there are more processors that do not necessarily require the `resultName` field. The result of this processor is sent onwards to the `csvConverter` processor.

	{
        "id": "csvConverter",
        "name": "tuktu.csv.processors.CSVReaderProcessor",
        "result": "",
        "config": {
            "field": "line",
            "remove_original": true,
            "headers_from_first": true
        },
        "next": [
            "locationFilter"
        ]
    }

The next step is to turn our line of text into a CSV line using the `tuktu.csv.processors.CSVReaderProcessor`, a processor from the [modules/csv](CSV module). This processor can take a line of text that is actually character-separated (not necessarily comma-separated) and uses some method of turning each field into an actual field of our data. For example, if this processor is given two lines where the first line contains the headers `header1,header2` and the second line contains the data `value1,value2`, it will be transformed into a mapping with the values `header1 -> value1` and `header2 -> value2`. These new fields (`header1` and `header2`) will hence become available to subequent processors. We do exactly the same here, where we use the fact that our input CSV contains headers on the first line to our advantage to populate the keys of our mapping. We next pass on the result to the `locationFilter` processor.

	{
        "id": "locationFilter",
        "name": "tuktu.processors.InclusionProcessor",
        "result": "",
        "config": {
            "expression": "Location=No Location,Location=On or near Conference/Exhibition Centre,Location=On or near Further/Higher Educational Building",
            "type": "simple",
            "and_or": "or"
        },
        "next": [
            "outcomeFilter"
        ]
    }

The `locationFilter` processors is of type `tuktu.processors.InclusionProcessor` - a processor that can include (and hence exclude) specific data packets. For our task, we have two fields to filter on (`Location` and `Crime type`). Within each field, we have values that we are after, this is hence an OR-filter. We want to have both `Location` and `Crime type` to match the right values though and this is an AND-filter. We cannot mix AND- and OR-filters in one processor, so we split the filtering into two. Notice how - due to our previous processor - we now have the actual CSV header names available as fields to filter on as can be seen from the `expression` configuration field.

	{
        "id": "outcomeFilter",
        "name": "tuktu.processors.InclusionProcessor",
        "result": "",
        "config": {
            "expression": "Crime type=Other crime,Crime type=Other theft",
            "type": "simple",
            "and_or": "or"
        },
        "next": [
            "debug", "writer"
        ]
    }

From our previous filter, we continue processing all data packets that have the right location. For thos data packets, we now filter on the right `Crime type` in a similar way. Note how we now send data on to two next processors. This is how Tuktu allows you to split data streams.

	{
		"id": "writer",
		"name": "tuktu.csv.processors.CSVWriterProcessor",
		"result": "",
		"config": {
			"file_name": "data/my_first_tuktu_output.csv"
		},
		"next": []
	}

One of the two final processors is the `writer` processor, of type `tuktu.csv.processors.CSVWriterProcessor`. This processor is from the [modules/csv](CSV module) once again and does the inverse of our `tuktu.csv.processors.CSVReaderProcessor` in that it turns our data's map into a single line of CSV. Additionally, it also writes this out to a file specified by the `file_name` configuration parameter. Once all our data packets have been processed, this processor will release the file and the ouput will be available.

	{
        "id": "debug",
        "name": "tuktu.processors.ConsoleWriterProcessor",
        "result": "",
        "config": {},
        "next": []
    }

The `debug` processor, of type `tuktu.processors.ConsoleWriterProcessor`, is a very simple processor that just writes data packets' contents out to stdout. It is often wise to use the `tuktu.processors.ConsoleWriterProcessor` processor during testing and creation of your Tuktu job.

## Meta Processors
Tuktu typically follows a transactional flow within a single actor on a single node. There are however, numerous processors that break this default data flow.

### Distributed Functionality
As a special kind of meta-processing, Tuktu has built-in functionalities for distributed computing.
