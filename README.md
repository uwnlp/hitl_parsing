# Human-in-the-Loop Parsing

## Installation

* `git clone https://github.com/luheng/hitl_parsing`

* `cd hitl_parsing`

* `./setup.sh`

* `ant`

* `./run.sh` (for replicating results on ccg dev)

* `./run.sh -ccg-test` (for replicating results on ccg test)

## Datasets

As specified in corpora.properties:
* [PennTreebank](https://catalog.ldc.upenn.edu/LDC95T7): (used to get the dev split of CCGBank) needs to be located at `hitl_parsing/testfiles/wsj/COMBINED/WSJ/`
* [CCGBank](https://catalog.ldc.upenn.edu/LDC2005T13): needs to be located at `hitl_parsing/testfiles/ccgbank/data/`

## Other functionalities (coming soon)

1. Viewing annotated QA data
2. Human-in-the-loop Demo
3. Stand-alone question generator


## Reference

This human-in-the-loop framework is described in the following paper:

  <i>Human-in-the-Loop Parsing</i> <br>
  Luheng He, Julian Michael, Mike Lewis and Luke Zettlemoyer <br>
  In proceedings of EMNLP 2016 <br>

## Contact

For questions about our code and data, please contact: luheng at cs dot washington dot edu
