import requests

from data.request import Request


class M3U8:
    def __init__(self, req):
        req.headers["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.149 Safari/537.36"
        
        self.req = req
        self.session = requests.Session()
        self.parts = []
        self.content_length = 0

    def parse(self):
        res = self.session.get(self.req.url, headers=self.req.headers, cookies=self.req.cookies)
        lines = []

        for line in res.text.split("\n"):
            if not line.startswith("#") and line.strip() != "":
                lines.append(line)
        return lines
    
    def extract(self, lines):
        for line in lines:
            res = self.session.options(self.req.url, headers=self.req.headers, cookies=self.req.cookies)
            content_type = res.headers.get("content-type").lower()
            print(res.status_code)
            if content_type == "application/vnd.apple.mpegurl" or content_type == "application/x-mpegurl":
                self.req.url = line
                self.extract(self.parse())
            else:
                self.content_length += int(res.headers.get("content-length"))
        
        print(self.content_length)
