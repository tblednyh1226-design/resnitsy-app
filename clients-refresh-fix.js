/* Keep client list in sync immediately after creating a client. */
window.newClient=function(done){
  modal('Новый клиент','',
    '<div class="field"><label>Имя</label><input id="nn" placeholder="Имя"></div><div class="field"><label>Телефон</label><input id="np" placeholder="Телефон" type="tel"></div><div class="field"><label>Мессенджер</label><select id="nm"><option>Telegram</option><option>WhatsApp</option><option>VK</option><option>MAX</option></select></div><div id="newClientErr" class="sub" style="color:#a33;min-height:18px"></div>',
    '<button class="btn primary" id="ns">Сохранить</button>'
  );
  const save=document.getElementById('ns'),err=document.getElementById('newClientErr');
  save.onclick=async()=>{
    err.textContent='';save.disabled=true;save.textContent='Сохраняю…';
    try{
      const z=await edge('createClient',{name:document.getElementById('nn').value,phone:document.getElementById('np').value,messenger:document.getElementById('nm').value});
      const c={id:z.client.id,name:z.client.display_name||z.client.name||document.getElementById('nn').value};
      closeModal();
      if(typeof done==='function')done(c);
      const clientsScreen=document.getElementById('clients');
      if(clientsScreen&&clientsScreen.classList.contains('on')&&typeof renderClientRows==='function'){
        const search=document.getElementById('clientSearch');
        if(search)search.value='';
        await renderClientRows('');
      }
    }catch(e){err.textContent=e.message||'Не удалось сохранить клиента';save.disabled=false;save.textContent='Сохранить'}
  };
};
