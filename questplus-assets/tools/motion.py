"""Asymmetric pocket draw: physical player axes, degrees, model pixels."""
import math
import numpy as np

DURATION=1.2  # Authored curve timing; exports retime open/close together.
TRANSITION_SECONDS=1.0
FIRST_PERSON_ARM_SCALE=.45
TIMES=[round(i/100,2) for i in range(121)]
HELD=np.array([0,18.8,-10.5])
RIGHT_REST=[53,0,6]
LEFT_REST=[53,0,-6]

def rotation(v):
    x,y,z=np.radians(v);cx,sx=math.cos(x),math.sin(x);cy,sy=math.cos(y),math.sin(y);cz,sz=math.cos(z),math.sin(z)
    return np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])@np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])@np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])

def curve(points,t):
    """C1 Hermite curve with stationary endpoints; authored anticipation and settle."""
    times=np.array([p[0] for p in points]);values=np.array([p[1] for p in points],float)
    if t<=times[0]:return values[0]
    if t>=times[-1]:return values[-1]
    i=int(np.searchsorted(times,t))-1;span=times[i+1]-times[i];s=(t-times[i])/span
    def tangent(j):
        if j==0 or j==len(points)-1:return np.zeros(3)
        if np.allclose(values[j-1],values[j]) or np.allclose(values[j],values[j+1]):return np.zeros(3)
        return (values[j+1]-values[j-1])/(times[j+1]-times[j-1])
    return (2*s**3-3*s**2+1)*values[i]+(s**3-2*s**2+s)*span*tangent(i)+(-2*s**3+3*s**2)*values[i+1]+(s**3-s**2)*span*tangent(i+1)

def tracks_for(third=False):
    right=[(0,[0,0,0]),(.12,[-8,0,4]),(.24,[-16,0,7]),(.42,[12,-8,20]),(.70,[46,-5,16]),(.99,[55,0,8]),(DURATION,RIGHT_REST)]
    left=[(0,[0,0,0]),(.48,[0,0,0]),(.66,[10,0,-10]),(.96,[43,0,-8]),(DURATION,LEFT_REST)]
    orient=[(0,[-90,0,-90]),(.24,[-90,0,-90]),(.46,[-55,55,-55]),(.72,[-18,140,-18]),(1.0,[0,180,0]),(DURATION,[0,180,0])]
    # A point on the right palm remains registered to the same part of the prop.
    shoulder=np.array([5,22,0]);palm=np.array([2.2,-10,-2])
    final_grip=shoulder+rotation(RIGHT_REST)@palm
    local_grip=rotation([0,180,0]).T@(final_grip-HELD)
    rr=[curve(right,t) for t in TIMES];lr=[curve(left,t) for t in TIMES];tr=[curve(orient,t) for t in TIMES]
    centers=[shoulder+rotation(r)@palm-rotation(q)@local_grip for r,q in zip(rr,tr)]
    positions=[center-HELD for center in centers]
    def rounded(values):return [[round(float(x),6) for x in v] for v in values]
    tracks={'rightArm':{'rotation':(TIMES,rounded(rr))},'leftArm':{'rotation':(TIMES,rounded(lr))},'tablet_root':{'position':(TIMES,rounded(positions)),'rotation':(TIMES,rounded(tr))}}
    if not third:
        right_rise=[(0,[0,0,0]),(.24,[0,-12,2]),(.50,[0,-2,.4]),(.78,[0,0,0]),(DURATION,[0,0,0])]
        left_rise=[(0,[0,-14,0]),(.48,[0,-14,0]),(.70,[0,-7,0]),(1.04,[0,0,0]),(DURATION,[0,0,0])]
        for bone,path in [('rightArm',right_rise),('leftArm',left_rise)]:tracks[bone]['position']=(TIMES,rounded([curve(path,t) for t in TIMES]))
        tracks['tablet_root']['position']=(TIMES,rounded([p+curve(right_rise,t) for p,t in zip(positions,TIMES)]))
    if third:
        head=[(0,[0,0,0]),(.34,[0,0,0]),(.75,[-10,0,0]),(DURATION,[-17,0,0])]
        tracks['head']={'rotation':(TIMES,rounded([curve(head,t) for t in TIMES]))}
    return tracks

def clear_palm_contacts(tracks,elements):
    """Move the prop only enough to keep the vanilla cuboid hands on its surface."""
    from validate_assets import bounds, overlaps, mat
    cubes=[]
    for source in elements:
        e=dict(source)
        for field in ('from','to','origin'):e[field]=list(np.array(source[field])*.65+HELD)
        cubes.append(e)
    offsets=[]
    for i,t in enumerate(TIMES):
        rot=tracks['tablet_root']['rotation'][1][i];pos=np.array(tracks['tablet_root']['position'][1][i]);normal=rotation(rot)@np.array([0,0,-1])
        shapes=[bounds(e,mat(HELD,rot,pos)) for e in cubes]
        hands=[]
        for bone,sign in [('rightArm',1),('leftArm',-1)]:
            hand={'from':[4 if sign>0 else -8,12,-2],'to':[8 if sign>0 else -4,15,2],'origin':[0,0,0],'rotation':[0,0,0]}
            shift=tracks[bone].get('position',(TIMES,[[0,0,0]]*len(TIMES)))[1][i]
            hands.append(bounds(hand,mat([sign*5,22,0],tracks[bone]['rotation'][1][i],shift)))
        def collision(offset):
            for hand in hands:
                hmin,hmax=hand.min(axis=0),hand.max(axis=0)
                for p in shapes:
                    p=p-normal*offset
                    if np.any(hmax<=p.min(axis=0)) or np.any(p.max(axis=0)<=hmin):continue
                    if overlaps(hand,p):return True
            return False
        offset=0
        while collision(offset):
            offset+=.05
            assert offset<5, f'Grip needs reauthoring at {t}s'
        offsets.append(offset+.08 if offset else 0)
    # A max envelope before averaging preserves clearance and removes contact-point chatter.
    def spread(values,r):return [max(values[max(0,i-r):min(len(values),i+r+1)]) for i in range(len(values))]
    envelope=spread(offsets,2)
    smoothed=[sum(envelope[max(0,i-2):min(len(envelope),i+3)])/len(envelope[max(0,i-2):min(len(envelope),i+3)]) for i in range(len(envelope))]
    assert smoothed[-1]<1e-6, 'Held pose must be clear without correction'
    for i,offset in enumerate(smoothed):
        normal=rotation(tracks['tablet_root']['rotation'][1][i])@np.array([0,0,-1])
        tracks['tablet_root']['position'][1][i]=[round(float(v),6) for v in np.array(tracks['tablet_root']['position'][1][i])-normal*offset]
    # The pocket trajectory occludes the prop; its physical size never changes.
    tracks['tablet_root']['scale']=([0,DURATION],[[1,1,1],[1,1,1]])
    if 'position' in tracks['rightArm']:
        # Shrink the real arms about the grip, keeping their palm contacts stationary.
        for name,sign in [('rightArm',1),('leftArm',-1)]:
            for i in range(len(TIMES)):
                delta=rotation(tracks[name]['rotation'][1][i])@((1-FIRST_PERSON_ARM_SCALE)*np.array([sign,-10,0]))
                tracks[name]['position'][1][i]=[round(float(v),6) for v in np.array(tracks[name]['position'][1][i])+delta]
            tracks[name]['scale']=([0,DURATION],[[FIRST_PERSON_ARM_SCALE]*3,[FIRST_PERSON_ARM_SCALE]*3])
            for i in range(len(TIMES)):
                old=tracks[name]['rotation'][1][i];new=[180-old[0],old[1],old[2]]
                palm=FIRST_PERSON_ARM_SCALE*np.array([sign,-10,0])
                delta=rotation(old)@palm-rotation(new)@palm
                tracks[name]['position'][1][i]=[round(float(v),6) for v in np.array(tracks[name]['position'][1][i])+delta]
                tracks[name]['rotation'][1][i]=new
    return tracks
