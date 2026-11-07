async page=>{
 const queries=['benchmod499','resource_99','ressource','missing_resource_zzzz'];
 const rows=[],errors=[];page.on('pageerror',error=>errors.push(error.message));
 const input=page.getByRole('textbox',{name:'Search registry resources',exact:true});
 for(let repeat=0;repeat<5;repeat++)for(const query of queries){
  const start=Date.now();await input.fill(query);await page.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))));
  const result=await page.evaluate(()=>({count:document.querySelector('.picker-count').textContent,nodes:document.querySelectorAll('.registry-virtual button').length,heap:performance.memory?.usedJSHeapSize}));
  if(result.nodes>300)throw Error('Registry is not virtualized');rows.push({repeat,query,ms:Date.now()-start,...result});
 }
 await input.fill('');await page.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))));
 await page.screenshot({path:'output/playwright/studio-load-registry.png'});
 await page.evaluate(data=>{window.__studioLoad=data},{rows,errors,kind:'development browser, 500 mods / 50000 items / 2500 tags'});
 if(errors.length)throw Error(errors.join('\n'));
}
