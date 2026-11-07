import {monitorEventLoopDelay} from 'node:perf_hooks';
const delay=monitorEventLoopDelay({resolution:10});delay.enable();
setInterval(()=>{if(process.connected)process.send({type:'metrics',rss:process.memoryUsage().rss,heap:process.memoryUsage().heapUsed,p99:delay.percentile(99)/1e6,max:delay.max/1e6});delay.reset()},250).unref();
