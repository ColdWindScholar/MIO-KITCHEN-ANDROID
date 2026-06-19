#!/bin/sh
script_path=$1

export EXECUTOR_PATH=$({EXECUTOR_PATH})
export START_DIR=$({START_DIR})
export TEMP_DIR=$({TEMP_DIR})
export SDCARD_PATH=$({SDCARD_PATH})
export ROOT_PERMISSION=$({ROOT_PERMISSION})
export SH_DIR=$START_DIR/script
export TMPDIR=$TEMP_DIR
export XBJ=$TEMP_DIR/.xm
export TOOLKIT=$({TOOLKIT})
export bin=$TOOLKIT
[ -f $TOOLKIT/environment.sh ]&&source $TOOLKIT/environment.sh

if [ $ROOT_PERMISSION = "true" ];then
export mdir=/data/MIO
else
export mdir=$START_DIR/MIO
fi
export zml=$SDCARD_PATH/MIO
if [ ! -d $zml ]; then
mkdir $zml
fi
if [ ! -d $mdir ]; then
mkdir $mdir
fi
export LD_LIBRARY_PATH=$START_DIR/bin/:$TOOLKIT
if [[ ! $TOOLKIT = "" ]]; then
    PATH=$TOOLKIT:$PATH
fi
if [[ $START_DIR != "" ]] && [[ -d $START_DIR ]];then
    cd $START_DIR
fi

if [[ -f $script_path ]]; then
    chmod 777 $script_path
    source $script_path
else
    echo "${script_path} 已丢失" 1>&2
fi