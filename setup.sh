#!/usr/bin/env bash

# Fail if we reference any unbound environment variables.
set -u

# Check if external data needs to be downloaded.
resource_url=https://dada.cs.washington.edu/qasrl/hitl_resources
model_url=lil.cs.washington.edu/resources
model_dir=./model_tritrain_finetune
resource_dir=./resources
lib_dir=./lib

if [ ! -e $model_dir ]
then
    echo "Downloading pre-trained CCG parsing model from $model_url"
    supertagmodel=model_tritrain_finetune.tgz
    curl -o $supertagmodel $model_url/$supertagmodel
    tar -xzf $supertagmodel
    rm $supertagmodel
else
    echo "Using cached data from $model_dir"
fi

if [ ! -e $resource_dir ]
then
    echo "Downloading HITL resources from $resource_url"
    curl -o resources.tar.gz "$resource_url/resources.tar.gz"
    tar -xvzf resources.tar.gz
    rm resources.tar.gz

    curl -o lib.tar.gz "$resource_url/lib.tar.gz"
    tar -xvzf lib.tar.gz
    rm lib.tar.gz
else
    echo "Using cached data from $resource_dir"
fi
