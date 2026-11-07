async page=>{
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.evaluate(()=>window.__studioOverload={publishRequests:0,jobOverloads:0,heartbeatOverloads:0});
 page.on('request',async request=>{if(request.method()==='POST'&&request.url().endsWith('/publish'))await page.evaluate(()=>window.__studioOverload.publishRequests++)});
 let busyJob=true;
 await page.route('**/jobs/*',async route=>{
  if(!busyJob)return route.continue();busyJob=false;
  await page.evaluate(()=>window.__studioOverload.jobOverloads++);
  await route.fulfill({status:503,headers:{'Retry-After':'1'},contentType:'application/json',body:'{"error":"Benchmark temporary overload"}'});
 });
 await page.route('**/ping',async route=>{
  await page.evaluate(()=>window.__studioOverload.heartbeatOverloads++);
  await route.fulfill({status:503,headers:{'Retry-After':'5'},contentType:'application/json',body:'{"error":"Benchmark temporary overload"}'});
 });
 await page.getByRole('navigation').getByRole('button',{name:'Benchmark quest 0',exact:true}).click();
 const input=page.getByRole('textbox',{name:'Title (English)',exact:true}),edits=[];
 for(let i=0;i<5;i++){
  const start=Date.now();await input.fill('Benchmark quest edited '+i);
  await page.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))));edits.push(Date.now()-start);
 }
 await page.evaluate(data=>{window.__studioConnectedLoad={...window.__studioConnectedLoad,...data,nodes:document.querySelectorAll('*').length,heap:performance.memory?.usedJSHeapSize}},{edits,errors});
 await page.screenshot({path:'output/playwright/studio-load-connected.png'});
 await page.getByRole('button',{name:'Save to server',exact:true}).click();
 await page.getByRole('status').filter({hasText:'Saving'}).waitFor({state:'attached'});
}
