#!/usr/bin/env bash

WEB_PATH="paicoding-web"
JAR_NAME="paicoding-web-0.0.1-SNAPSHOT.jar"
GC_LOG_PATH="${GC_LOG_PATH:-./logs/gc.log}"
JAVA_HEAP_OPTS="${JAVA_HEAP_OPTS:--Xms2g -Xmx2g -XX:MaxGCPauseMillis=50}"
JAVA_GC_OPTS="${JAVA_GC_OPTS:--XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./logs/heapdump.hprof -Xlog:gc*:file=${GC_LOG_PATH}:time,uptime,level,tags}"
JAVA_RUNTIME_OPTS="${JAVA_RUNTIME_OPTS:--Dspring.devtools.restart.enabled=false -XX:NativeMemoryTracking=summary -XX:-OmitStackTraceInFastThrow}"

# 部署
function start() {
    git pull

    # 杀掉之前的进程
    cat pid.log| xargs -I {} kill {}
    mv ${JAR_NAME} ${JAR_NAME}.bak

    mvn clean install -Dmaven.test.skip=True -Pprod
    cd ${WEB_PATH}
    mvn clean package spring-boot:repackage -Dmaven.test.skip=true -Pprod
    cd -

    mv ${WEB_PATH}/target/${JAR_NAME} ./
    run
}

# 重启
function restart() {
    # 杀掉之前的进程
    cat pid.log| xargs -I {} kill {}
    # 重新启动
    run
}

function run() {
  mkdir -p ./logs
  echo "启动脚本：==========="
  echo "nohup java -server ${JAVA_HEAP_OPTS} ${JAVA_GC_OPTS} ${JAVA_RUNTIME_OPTS} -jar ${JAR_NAME} > /dev/null 2>&1 &"
  echo "==========="
  nohup java -server ${JAVA_HEAP_OPTS} ${JAVA_GC_OPTS} ${JAVA_RUNTIME_OPTS} -jar ${JAR_NAME} > /dev/null 2>&1 &
  echo $! 1> pid.log
}

if [ $# == 0 ]; then
  echo "miss command: start | restart"
elif [ $1 == 'start' ]; then
  start
elif [ $1 == 'restart' ];then
  restart
else
  echo 'illegal command, support cmd: start | restart'
fi