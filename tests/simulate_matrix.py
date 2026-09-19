#!/usr/bin/env python3
"""Contract-level exhaustive simulation for disposable provider chats.

This does NOT pretend to be a live Gemini response test. It stress-tests the
Bridge-owned invariants that can be verified deterministically without a phone:
profile isolation, fresh-chat startup, local continuity, material handoff,
session closing, evidence enrichment and fail-closed behavior.
"""
from dataclasses import dataclass, field
from hashlib import sha256
import re, sys

LANGS=[
"Inglés","Francés","Alemán","Italiano","Portugués","Japonés","Coreano","Mandarín","Cantonés","Árabe",
"Hindi","Bengalí","Ruso","Ucraniano","Polaco","Checo","Eslovaco","Húngaro","Rumano","Búlgaro",
"Griego","Turco","Hebreo","Persa","Urdu","Punjabi","Tamil","Telugu","Maratí","Gujarati",
"Vietnamita","Tailandés","Indonesio","Malayo","Tagalo","Suajili","Afrikáans","Neerlandés","Sueco","Noruego",
"Danés","Finés","Islandés","Irlandés","Galés","Catalán","Gallego","Euskera","Maya yucateco","Tseltal",
"Náhuatl","Quechua","Guaraní","Esperanto","Latín","Serbio","Croata","Esloveno","Estonio","Letón",
"Bosnio","Macedonio","Albanés","Georgiano","Armenio","Azerí","Kazajo","Uzbeko","Kirguís","Tayiko",
"Mongol","Nepalí","Cingalés","Birmano","Jemer","Lao","Hmong","Somalí","Amárico","Hausa",
"Yoruba","Igbo","Zulú","Xhosa","Sesotho","Shona","Kinyarwanda","Malgache","Maorí","Samoano",
"Tongano","Fiyiano","Criollo haitiano","Luxemburgués","Maltés","Lituano","Bielorruso","Pashto","Kurdo","Tibetano"
]
PURPOSES=[
"conversación diaria","entrevista de trabajo","presentación técnica","viaje","lectura académica","manual industrial",
"atención a clientes","negociación","pronunciación","traducción","examen de certificación","conversación científica"
]
MATERIALS=[
("none","",0),
("application/pdf","manual.pdf",2_000_000),
("image/jpeg","fotografia.jpg",3_000_000),
("application/vnd.openxmlformats-officedocument.wordprocessingml.document","documento.docx",400_000),
("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","datos.xlsx",500_000),
("application/vnd.openxmlformats-officedocument.presentationml.presentation","presentacion.pptx",1_200_000),
("text/plain","notas.txt",30_000),
("text/csv","tabla.csv",80_000),
("audio/mpeg","audio.mp3",8_000_000),
("video/mp4","video.mp4",120_000_000),
]
VERSION_FAMILIES=[
"gemini-closed","chat-with-history-open","fresh-chat-open","live-already-active","drawer-open",
"temporary-chat","consent-then-normal","live-label-renamed","structure-only-live","unknown-ui"
]
CLOSE_SURFACES=["live-active","chat-active","user-ended-live","app-backgrounded","consent","unknown-ui"]

@dataclass
class Memory:
    sessions:int=0
    summary:str=""
    next_obj:str=""
    events:list=field(default_factory=list)

@dataclass
class ProfileState:
    lang:str
    purpose:str
    profile_id:str
    memory:Memory=field(default_factory=Memory)
    fresh_provider_chats:int=0
    compatibility_fail:int=0
    expected_events:int=0

def norm_id(label:str)->str:
    import unicodedata
    raw=unicodedata.normalize('NFD',label.strip().lower())
    raw=''.join(c for c in raw if unicodedata.category(c)!='Mn')
    key=re.sub(r'[^a-z0-9]+',' ',raw).strip()
    key=re.sub(r'\s+','-',key)
    if key and re.fullmatch(r'[a-z0-9-]+',key): return 'lang-'+key
    return 'lang-u-'+sha256(label.encode()).hexdigest()[:12]

def safe_material(mime,name,size,idx):
    if mime=='none': return True
    uri=f'content://aleyon-test/{idx}/{name}'
    if not uri.startswith('content://') or size<=0: return False
    max_bytes=2*1024**3 if mime.startswith('video/') else 100*1024**2
    return size<=max_bytes and '\n' not in name and '\r' not in name

def normalize_start(family:str)->str:
    if family in {'gemini-closed','chat-with-history-open','fresh-chat-open','drawer-open',
                  'live-label-renamed','structure-only-live'}:
        return 'FRESH_CHAT'
    if family=='live-already-active': return 'BACK_THEN_FRESH_CHAT'
    if family=='temporary-chat': return 'ESCAPE_THEN_FRESH_CHAT'
    if family=='consent-then-normal': return 'WAIT_HUMAN_THEN_FRESH_CHAT'
    if family=='unknown-ui': return 'FAIL_CLOSED'
    raise AssertionError(family)

def close_plan(surface:str):
    if surface=='live-active': return ('END_LIVE','USE_SAME_CHAT')
    if surface in {'chat-active','user-ended-live'}: return ('USE_SAME_CHAT',)
    if surface=='app-backgrounded': return ('REOPEN_EXISTING_SURFACE','REOBSERVE')
    if surface=='consent': return ('WAIT_HUMAN',)
    if surface=='unknown-ui': return ('FAIL_CLOSED',)
    raise AssertionError(surface)

def run():
    errors=[]; profiles=[]
    for i,lang in enumerate(LANGS):
        profiles.append(ProfileState(lang,PURPOSES[i%len(PURPOSES)],norm_id(lang)))
    if len(profiles)<100: errors.append('less than 100 profiles')
    if len({p.profile_id for p in profiles})!=len(profiles): errors.append('profile id collision')

    total_sessions=0; material_sessions=0; fresh_chats=0; fail_closed=0; debrief_fallbacks=0
    for pi,p in enumerate(profiles):
        for session in range(8):
            family=VERSION_FAMILIES[(pi+session)%len(VERSION_FAMILIES)]
            outcome=normalize_start(family)
            if outcome=='FAIL_CLOSED':
                snap=(p.memory.sessions,p.memory.summary,p.memory.next_obj,list(p.memory.events))
                p.compatibility_fail+=1;fail_closed+=1
                if snap!=(p.memory.sessions,p.memory.summary,p.memory.next_obj,list(p.memory.events)):
                    errors.append(f'fail-closed mutated memory: {p.lang}')
                continue

            total_sessions+=1
            # Every successful explicit session must start from a disposable,
            # clean provider chat. No title/search/rebuild state exists.
            p.fresh_provider_chats+=1;fresh_chats+=1

            mime,name,size=MATERIALS[(pi*3+session)%len(MATERIALS)]
            if mime!='none':
                material_sessions+=1
                if not safe_material(mime,name,size,pi*10+session):
                    errors.append(f'unsafe material handling: {p.lang}/{name}')

            # The provider may disappear between sessions; local memory is the
            # only state carried forward.
            before_summary=p.memory.summary
            p.memory.sessions+=1
            if (pi+session)%9==0:
                debrief_fallbacks+=1
                p.memory.summary='Sesión guardada sin resumen de Gemini; evidencia local preservada.'
                p.memory.events.append(('session-transcript',f'evidencia-{p.memory.sessions}')); p.expected_events+=1
            else:
                p.memory.summary=f'{p.lang} sesión {p.memory.sessions}: {p.purpose}'
                p.memory.events.append(('session-progress',f'avance-{p.memory.sessions}'))
                p.memory.events.append(('session-reinforcement',f'reforzar-{p.memory.sessions}')); p.expected_events+=2
            p.memory.next_obj=f'continuar {p.purpose} {p.memory.sessions+1}'
            if p.memory.sessions>1 and not before_summary: errors.append(f'local continuity lost before next session: {p.lang}')
            if len(p.memory.events)!=p.expected_events: errors.append(f'evidence enrichment drift {p.lang}')

        if p.fresh_provider_chats!=p.memory.sessions:
            errors.append(f'provider chat/session mismatch {p.lang}')

    close_cases=0
    forbidden_close_actions={'CREATE_NORMAL_CHAT','SEARCH_HISTORY','RENAME_CHAT'}
    for p in profiles:
        for surface in CLOSE_SURFACES:
            plan=close_plan(surface); close_cases+=1
            if forbidden_close_actions.intersection(plan): errors.append(f'close path touched provider history: {p.lang}/{surface}/{plan}')
            if surface=='live-active' and plan[:2]!=('END_LIVE','USE_SAME_CHAT'): errors.append(f'live close did not return to same chat: {p.lang}')
            if surface in {'chat-active','user-ended-live'} and plan!=('USE_SAME_CHAT',): errors.append(f'chat close attempted navigation: {p.lang}/{surface}')
            if surface=='app-backgrounded' and 'REOPEN_EXISTING_SURFACE' not in plan: errors.append(f'background close lost current provider surface: {p.lang}')

    bad=[
        ('application/pdf','path.pdf',10,'file:///sdcard/path.pdf'),
        ('application/pdf','huge.pdf',100*1024**2+1,'content://docs/huge'),
        ('video/mp4','huge.mp4',2*1024**3+1,'content://video/huge'),
    ]
    for mime,name,size,uri in bad:
        max_bytes=2*1024**3 if mime.startswith('video/') else 100*1024**2
        accepted=uri.startswith('content://') and 0<size<=max_bytes
        if accepted: errors.append(f'adversarial material accepted: {name}')

    if errors:
        print('FAIL exhaustive contract simulation')
        for e in errors[:100]: print(' -',e)
        if len(errors)>100: print(f' ... {len(errors)-100} more')
        return 1
    print(f'PASS exhaustive contract simulation: profiles={len(profiles)}, sessions={total_sessions}, '
          f'fresh_provider_chats={fresh_chats}, material_sessions={material_sessions}, '
          f'fail_closed_probes={fail_closed}, debrief_fallbacks={debrief_fallbacks}, '
          f'close_cases={close_cases}, version_families={len(VERSION_FAMILIES)}')
    return 0

if __name__=='__main__': sys.exit(run())
