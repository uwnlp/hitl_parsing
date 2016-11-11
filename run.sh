#!/usr/bin/env bash
CLASSPATH=bin:lib/*
java -Xmx8000m -cp $CLASSPATH edu.uw.easysrl.qasrl.main.CcgReparsingExperiment
