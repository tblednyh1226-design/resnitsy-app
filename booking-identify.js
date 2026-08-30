/* Phone-first identification for online booking */
(function(){
  const ID_API='https://acukaqoguzkrphauovhk.supabase.co/functions/v1/resnitsy-client-identity';
  async function identify(phone){
    const r=await fetch(ID_API,{method:'POST',headers:{'Content-Type':'text/plain;charset=UTF-8'},body:JSON.stringify({phone}),cache:'no-store'});
    const j=await r.json();
    if(!r.ok||!j.ok)throw Error(j.error||'Не удалось проверить номер');
    return j;
  }
  function makeSection(id,html){const s=document.createElement('section');s.id=id;s.className='step';s.innerHTML=html;return s}
  const s1=document.getElementById('s1'); if(!s1)return;
  document.querySelectorAll('.step').forEach(x=>x.classList.remove('on'));
  const s0=makeSection('s0','<div class="scroll"><h3>Ваш номер телефона</h3><div class="sub" style="margin-bottom:12px">По номеру мы проверим, были ли вы у нас раньше</div><div class="field"><label>Телефон</label><input id="identityPhone" type="tel" autocomplete="tel" inputmode="tel" placeholder="+7 999 123-45-67"></div><div id="identityError"></div></div><div class="actions one"><button id="identityNext" class="btn primary">Далее</button></div>');
  const sl=makeSection('s0last','<div class="scroll"><h3>Рады видеть вас снова 💗</h3><div id="lastServiceBox" class="summary" style="margin-top:10px"></div></div><div class="actions"><button id="chooseOther" class="btn">Выбрать другую</button><button id="repeatLast" class="btn primary">Повторить</button></div>');
  s1.parentNode.insertBefore(sl,s1); s1.parentNode.insertBefore(s0,sl); s0.classList.add('on');
  const phoneInput=document.getElementById('identityPhone'),next=document.getElementById('identityNext'),err=document.getElementById('identityError');
  let identity=null;
  function applyIdentity(j){
    identity=j;
    const p=phoneInput.value.trim();
    const cp=document.getElementById('clientPhone'),wp=document.getElementById('wishPhone'); if(cp){cp.value=p;cp.readOnly=true} if(wp)wp.value=p;
    if(j.client){
      if(document.getElementById('clientName'))document.getElementById('clientName').value=j.client.display_name||'';
      if(document.getElementById('wishName'))document.getElementById('wishName').value=j.client.display_name||'';
      if(j.client.messenger){const m=document.getElementById('messenger'),wm=document.getElementById('wishMessenger');if(m)m.value=j.client.messenger;if(wm)wm.value=j.client.messenger}
    }
  }
  next.onclick=async()=>{const p=phoneInput.value.trim();if(p.replace(/\D/g,'').length<10){err.innerHTML='<div class="error">Проверьте номер телефона</div>';return}next.disabled=true;next.textContent='Проверяю…';err.innerHTML='';try{const j=await identify(p);applyIdentity(j);if(j.client&&j.last_service){document.getElementById('lastServiceBox').innerHTML='<div class="sub">Ваша последняя услуга</div><strong style="display:block;font-size:19px;margin-top:5px">'+j.last_service.name+'</strong>';step('s0last')}else step('s1')}catch(e){err.innerHTML='<div class="error">'+e.message+'</div>'}finally{next.disabled=false;next.textContent='Далее'}};
  phoneInput.addEventListener('keydown',e=>{if(e.key==='Enter')next.click()});
  document.getElementById('chooseOther').onclick=()=>{selected.clear();document.querySelectorAll('#serviceList .card').forEach(x=>x.classList.remove('on'));document.getElementById('toPeriods').disabled=true;step('s1')};
  document.getElementById('repeatLast').onclick=()=>{if(!identity?.last_service)return step('s1');selected.clear();selected.add(identity.last_service.id);document.querySelectorAll('#serviceList .card').forEach(x=>x.classList.toggle('on',x.textContent.includes(identity.last_service.name)));document.getElementById('toPeriods').disabled=false;step('s2')};
  const change=document.createElement('button');change.type='button';change.className='btn';change.style='min-height:38px;padding:6px 10px;margin:4px 0 8px';change.textContent='← Изменить номер';change.onclick=()=>step('s0');const sc=s1.querySelector('.scroll');if(sc)sc.insertBefore(change,sc.firstChild);
})();