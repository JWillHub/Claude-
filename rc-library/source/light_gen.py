# Light mode, generated: every rule that paints text white (or a black panel) gets a light-mode twin
# that uses the dark text color instead. Things that sit on pictures or on a solid accent keep white.
import re
KEEP = ('.hcard', '.hero', '.bd', '.card .rb', '.card .num', '.chip.on', '.rdot', '.c9', '.c5', '.c4', '.c8', '.c7', '.c6',
        '.subprev', '.glback', '.cwplay', '.lcx', '.grid .rk', '.stamp', '.mtlogo', '.lplay', '.toast', '.ptr', 'html.light',
        '.gpk', '.lsearch', '#lsort', '.seapick', '.skel', '.updbtn', '#hcar', '.pill')
def gen(s):
    css = ''.join(re.findall(r'<style[^>]*>(.*?)</style>', s, re.S))
    css = re.sub(r'/\*.*?\*/', '', css, flags=re.S)
    css = re.sub(r'@keyframes[^{]*\{(?:[^{}]*\{[^{}]*\})*[^{}]*\}', '', css)
    out = []
    for m in re.finditer(r'([^{}@]+)\{([^{}]*)\}', css):
        sel, body = ' '.join(m.group(1).split()), m.group(2)
        if not sel or sel.startswith(('0%', '50%', '100%', 'from', 'to')): continue
        sels = [x.strip() for x in sel.split(',') if x.strip() and not any(k in x for k in KEEP) and '::-webkit' not in x]
        if not sels: continue
        pre = ','.join('html.light ' + x for x in sels)
        rules = []
        c = re.search(r'(?<![-\w])color:\s*(#fff\b|#ffffff|#f4efe9|#d3cbdb|rgba\(255,\s*255,\s*255,\s*([\d.]+)\)|rgba\(23\d,\s*23\d,\s*24\d,\s*([\d.]+)\))\s*(!important)?', body)
        if c:
            a = c.group(2) or c.group(3)
            col = 'var(--text)' if not a or float(a) > .8 else f'rgba(0,0,0,{min(.85, float(a) + .05):.2f})'
            rules.append(f'color:{col}!important')
        bg = re.search(r'background(-color)?:\s*(#000\b|#000000|#0c0a0f|#111\b|#120f17|#141119)\s*(!important)?\s*(;|$)', body)
        if bg: rules.append('background-color:#fff!important')
        if re.search(r'box-shadow:[^;]*\s#000\b', body) and bg: rules.append('box-shadow:none!important')
        b = re.search(r'border(-color)?:[^;]*rgba\(255,\s*255,\s*255,\s*([\d.]+)\)', body)
        if b: rules.append(f'border-color:rgba(0,0,0,{min(.3, float(b.group(2)) * .8 + .04):.2f})!important')
        if re.search(r'text-shadow:\s*0 1px 3px rgba\(0,0,0,\.9\)', body): rules.append('text-shadow:none!important')
        if rules: out.append(pre + '{' + ';'.join(rules) + '}')
    return s.replace('</head>', '<style id="lightgen">' + '\n'.join(out) + '\n' + LATE + '</style>\n</head>', 1)
# hand-written fixes that must come after the generated rules
LATE = '''html.light .hcard *,html.light .hero .hin *{color:#fff!important}
html.light .tb svg{filter:none!important}
html.light .shcard .shwrap .pad{box-shadow:0 -18px 18px #fff!important}
html.light .shbg{background-color:#ececef!important}
'''
