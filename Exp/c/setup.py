import setuptools

with open("README.md", "r", encoding="utf-8") as fh:
    long_description = fh.read()

setuptools.setup(
    name="c-pkg-eric-lian",
    version="0.0.1",
    author="Eric Lian",
    author_email="public@superexercisebook.com",
    description=long_description,
    long_description=long_description,
    long_description_content_type="text/markdown",
    url="https://github.com/paraparty",
    project_urls={
    },
    classifiers=[
        "Programming Language :: Python :: 3",
        "License :: OSI Approved :: MIT License",
        "Operating System :: OS Independent",
    ],
    package_dir={"": "src"},
    packages=setuptools.find_packages(where="src"),
    python_requires=">=3.6",

    install_requires=[
        "a_pkg==0.0.1",
        "b_pkg==0.0.1"
    ],
    dependency_links=[
        'https://pip.laptop.lhr.wiki/'
    ]
)
