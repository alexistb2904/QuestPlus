async page => {
 const save=async()=>{await page.getByRole('button',{name:'Enregistrer le brouillon',exact:true}).click();return page.evaluate(()=>JSON.parse(localStorage.getItem('questplus.quest-studio.v1')))};
 await page.setViewportSize({width:1440,height:1000});
 await page.locator('.quest-item').filter({hasText:'Maître du cuir'}).click();
 await page.getByRole('tab',{name:'Prérequis',exact:true}).click();
 const before=await save(),original=before.project.quests.find(q=>q.id===before.selection.id);
 const created=await page.getByLabel('Quête prérequise',{exact:true}).count()===0;
 if(created)await page.locator('.root-tools').getByRole('button',{name:'+ Quête',exact:true}).click();
 await page.getByLabel('Quête prérequise',{exact:true}).first().selectOption('questplus:equipment/armor_collector');
 await save();
 if(!await page.locator('.issues-bar.error').isVisible())throw Error('cycle must be reported');
 if(!await page.locator('.inspector').isVisible())throw Error('cycle crashed editor');
 await page.getByRole('button',{name:'Annuler',exact:true}).click();
 if(created)await page.getByRole('button',{name:'Annuler',exact:true}).click();
 const after=await save(),restored=after.project.quests.find(q=>q.id===before.selection.id);
 if(JSON.stringify(restored)!==JSON.stringify(original)||await page.locator('.issues-bar.error').count())throw Error('undo must recover original definition');
 return 'PASS: invalid prerequisite cycle remains editable and undo restores the definition';
}
