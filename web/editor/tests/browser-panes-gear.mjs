async page => {
 const assert=(value,message)=>{if(!value)throw Error(message)};
 await page.setViewportSize({width:1672,height:1000});
 await page.evaluate(()=>{localStorage.removeItem('questplus.quest-studio.v1');localStorage.removeItem('questplus.pane-sizes')});
 await page.reload();
 await page.getByRole('button',{name:'Master of Leather',exact:true}).click();
 const gear=page.locator('.quest-details .gear-item-editor:visible');
 assert(await gear.count()===4,'four armor pieces visible');
 const properties=page.locator('#properties-panel'),propsSeparator=page.getByRole('separator',{name:'Resize properties'});
 let before=(await properties.boundingBox()).width,box=await propsSeparator.boundingBox();
 await page.mouse.move(box.x+4,box.y+box.height/2);await page.mouse.down();
 await page.mouse.move(box.x-75,box.y+box.height/2,{steps:5});await page.mouse.up();
 const newWidth=(await properties.boundingBox()).width;
 assert(newWidth>=before+60,'pointer drag expands properties');
 const library=page.locator('.sidebar');before=(await library.boundingBox()).width;
 await page.getByRole('separator',{name:'Resize library'}).focus();await page.keyboard.press('ArrowRight');
 assert((await library.boundingBox()).width>before,'keyboard expands library');
 const details=page.locator('.quest-details');before=(await details.boundingBox()).height;
 await page.getByRole('separator',{name:'Resize diagram and objectives'}).focus();await page.keyboard.press('ArrowUp');
 assert((await details.boundingBox()).height>before,'keyboard expands objectives');
 const item=gear.first().getByLabel('Equipment item 1');await item.fill('mymod:crystal_helmet');
 await gear.first().getByRole('button',{name:'+ Enchantment'}).click();
 const enchantment=gear.first().locator('.gear-enchantment input').first();
 await enchantment.fill('mymod:strong_guard');await enchantment.blur();
 await page.waitForFunction(()=>{
  const data=JSON.parse(localStorage.getItem('questplus.quest-studio.v1')||'{}');
  const q=data.project?.quests?.find(q=>q.id==='questplus:equipment/set_leather');
  const items=q?.objectives?.[0]?.conditions?.['questplus:gear']?.items;
  return items?.[0]?.item==='mymod:crystal_helmet'&&items[0].enchantments?.['mymod:strong_guard']===1&&items[1].item==='minecraft:leather_chestplate';
 });
 // Browse the pinned 26.3 vanilla enchantment registry without altering custom IDs.
 await gear.first().getByRole('button',{name:'Browse enchantments'}).click();
 const chooser=page.getByRole('dialog',{name:'Choose enchantment'});
 assert(await chooser.isVisible(),'enchantment chooser opens');
 await chooser.getByRole('textbox',{name:'Search enchantments…'}).fill('unbreaking');
 await chooser.getByRole('button',{name:/Unbreaking/}).click();
 await page.waitForFunction(()=>{
   const value=JSON.parse(localStorage.getItem('questplus.quest-studio.v1')||'{}');
   const item=value.project?.quests?.find(q=>q.id==='questplus:equipment/set_leather')?.objectives?.[0]?.conditions?.['questplus:gear']?.items?.[0];
   return item?.enchantments?.['minecraft:unbreaking']===1&&item?.enchantments?.['mymod:strong_guard']===1;
 });
 await page.reload();await page.waitForSelector('#properties-panel');
 assert(Math.abs((await properties.boundingBox()).width-newWidth)<2,'panel width persists after reload');
 await page.setViewportSize({width:390,height:844});
 const horizontal=page.getByRole('separator',{name:'Resize diagram and objectives'});
 assert(await horizontal.isVisible(),'mobile objective separator visible');
 before=(await details.boundingBox()).height;await horizontal.focus();await page.keyboard.press('ArrowUp');
 assert((await details.boundingBox()).height>before,'keyboard resizes mobile objectives');
 assert(!await page.evaluate(()=>document.documentElement.scrollWidth>document.documentElement.clientWidth),'no page overflow on mobile');
 return 'Pane resizing, persistence, and modded equipment/enchantment editing passed';
}
