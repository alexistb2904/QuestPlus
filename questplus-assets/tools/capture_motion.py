"""Native Blockbench animation previews, sampled at 25 fps (not Minecraft footage)."""
import base64, io
from pathlib import Path
from PIL import Image
from blockbench_bridge import call

ROOT=Path(__file__).resolve().parents[1]
for mode in ['first_person','third_person']:
    call('load_project',{'path':str(ROOT/'blockbench'/f'questplus_{mode}.bbmodel')})
    call('execute_script',{'code':'Modes.options.animate.select(); return true;'})
    camera={'position':[0,25.6,1],'target':[0,18.8,-10.5]} if mode=='first_person' else {'position':[30,28,-40],'target':[0,19,-4]}
    call('set_camera_angle',camera)
    call('execute_script',{'code':'Preview.selected.camera.zoom=1; Preview.selected.camera.fov=65; Preview.selected.camera.updateProjectionMatrix(); return true;'})
    frames=[]
    durations=call('execute_script',{'code':'return Animation.all.map(a=>a.length);'})
    for index,duration in [(0,durations[0]),(1,.6),(2,durations[2])]:
        call('execute_script',{'code':'Animation.all[params.index].select(); return true;','params':{'index':index}})
        for step in range(round(duration*25)+1):
            t=step/25
            call('execute_script',{'code':'Timeline.setTime(params.time); Animator.preview(); return true;','params':{'time':t}})
            shot=call('screenshot',{'width':640,'height':480,'annotate':False})
            frames.append(Image.open(io.BytesIO(base64.b64decode(shot['base64']))).convert('RGB'))
    frames[0].save(ROOT/'previews'/f'blockbench_{mode}_motion.gif',save_all=True,append_images=frames[1:],duration=40,loop=0)
    print('Native 25fps preview:',mode,flush=True)
# Leave the outside opening at the two-hand handoff for inspection.
call('execute_script',{'code':'Animation.all[0].select(); Timeline.setTime(0.94); Animator.preview(); return true;'})
