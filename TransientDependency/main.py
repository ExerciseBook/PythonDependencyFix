from flask import Flask
from jinja2 import Template

app = Flask(__name__)


@app.route("/**")
def hello_world():
    t = Template("Hello, {{ something }}!")
    return t.render(something="World")
