import requests
import json


def resolve(name: str) -> object:
    r = requests.get('https://one.one.one.one/dns-query', params={
        "name": name
    })

    response = r.request
    content = response.body
    if content is None:
        return None
    return json.load(content)
