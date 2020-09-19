class AudioDetector {
    constructor() {
        this.urls = [];
        this.detectListener = null;
    }

    run(url, title, headers) {
        const detect = {
            data: {
                url: url
            }
        };
    }

    isIn(url) {
        return this.urls.includes(url);
    }

    getFileName(title, url, mime) {
        const urlNodes = url.split("?")[0].split("#")[0].split("/");
        const name = urlNodes[urlNodes.length - 1].split(".")[0];
        
    }
}