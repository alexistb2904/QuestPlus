// Run inside the Playwright Quest Studio session after the page is loaded.
async page => {
  const storage='questplus.quest-studio.v1';
  const defaultDraft=await page.evaluate(key=>localStorage.getItem(key),storage);
  const assert=(condition,message)=>{if(!condition)throw Error(message)};
  try {
    if(!defaultDraft) {
      await page.evaluate(key=>localStorage.setItem(key,JSON.stringify({
        project:{categories:[],quests:[],config:{schemaVersion:1,autosaveSeconds:5}},
        categoryId:null,selection:null,camera:{x:0,y:0,zoom:1},language:'en_us'
      })),storage);
    }
    await page.reload();
    assert(await page.locator('.startup-dialog').isVisible(),'resumption prompt shown');
    await page.locator('.startup-dialog .startup-choice').nth(0).click();
    assert(await page.getByRole('button',{name:/^Empty project$|^Projet vide$|^Proyecto vacío$|^空项目$/}).isVisible(),'empty option shown');
    await page.locator('.startup-dialog .startup-choice').nth(0).click();
    assert(await page.locator('.startup-dialog').count()===0,'new empty project opens');
    await page.waitForFunction(key=>{const p=JSON.parse(localStorage.getItem(key)||'null');return p?.project?.categories?.length===0},storage);
    assert(await page.evaluate(key=>JSON.parse(localStorage.getItem(key)).project.categories.length===0,storage),'empty project persists');
    await page.reload();
    assert(await page.locator('.startup-dialog').isVisible(),'empty saved project remains resumable');
    await page.locator('.startup-dialog .startup-choice').nth(0).click();
    await page.locator('.startup-dialog .startup-choice').nth(1).click();
    assert(await page.locator('.startup-dialog').count()===0,'example-based project opens');
    await page.waitForFunction(key=>JSON.parse(localStorage.getItem(key)||'null')?.project?.categories?.length>0,storage);
    assert((await page.evaluate(key=>JSON.parse(localStorage.getItem(key)).project.categories.length,storage))>0,'example project persists');
    await page.reload();
    assert(await page.locator('.startup-dialog').isVisible(),'saved examples prompt');
    await page.locator('.startup-dialog .startup-choice').nth(1).click();
    assert(await page.locator('.startup-dialog').count()===0,'resume restores editor');
  } finally {
    await page.evaluate(({key,value})=>value===null?localStorage.removeItem(key):localStorage.setItem(key,value),{key:storage,value:defaultDraft});
  }
}
