from data.request import Request
from core.parser_m3u8 import M3U8


m3u8 = M3U8(Request("https://vodhlsweb-vh.akamaihd.net/i/songs/92/3033392/29760671/29760671_64.mp4/master.m3u8?set-akamai-hls-revision=5&hdnts=st=1586290298~exp=1586304698~acl=/i/songs/92/3033392/29760671/29760671_64.mp4/*~hmac=bf83e364cc5a405157e228c3dbb054e56ea8f25316800082753afbf229e32308"))
initial_lines = m3u8.parse()
m3u8.extract(initial_lines)
