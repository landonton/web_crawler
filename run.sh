#!/bin/bash

javac crawl.java

# must provide valid url and number of hops
java crawl $1 $2