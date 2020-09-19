class Request:
    def __init__(self, url, headers={}, cookies={}):
        self.url = url
        self.headers = headers
        self.cookies = cookies