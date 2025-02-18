## Organisms
`Organisms` is a personal research project that introduces *rationality* into the Propagation Model of Computation: [Propagation Networks: A Flexible and Expressive Substrate for Computation](https://dspace.mit.edu/bitstream/handle/1721.1/54635/603543210-MIT.pdf?sequence=2&isAllowed=y).

In the book [Software Design for Flexibility](https://mitpress.mit.edu/9780262045490/software-design-for-flexibility/), the model is introduced as follows:

>The propagator model is built on the idea that the basic
computational elements are propagators—autonomous, independent
machines interconnected by shared cells through which they
communicate. Each propagator continuously examines the
cells it is connected to and adds information to some cells based on
computations it can make from information it obtains from others.
Cells accumulate information, and propagators produce information.

### Rationality Instead of Smartness
In section 6.4 of the [dissertation](https://dspace.mit.edu/bitstream/handle/1721.1/54635/603543210-MIT.pdf?sequence=2&isAllowed=y) on the propagators, the author presents an interesting discussion about the **Scheduler**—a piece of software that executes the network inh the model. A well-designed scheduler optimizes computation by prioritizing informative directions and avoiding redundancy.

Instead of making the scheduler smarter, I make it **rational**. This way, it does not need to be "smart"—it simply behaves rationally.

### Common Knowledge of Rationality
At any point in time, the neurons in the network might be invited to play a *game*. The rules are as follows: each neuron must decide whether to activate and determine who should go next. The optimal computational path, i.e., the solution to the game, can be derived through backward induction.

Since [common knowledge of rationality implies backward induction](http://www.ma.huji.ac.il/raumann/pdf/36.pdf), the solution to the game naturally follows from this principle.

But what if even cells could play a similar game? In that case, the optimal solution would also determine which variable should be tested first! We should encode this knowledge into a binary decision diagram—that would indeed be great!

### Demonstrative Front End
While a front end is not required to use this library, a minimal UI is available to help users understand the model. You can access the app [here](https://lucapanofsky.github.io/symbolic_neural_networks/). The UI is intentionally minimal and does not expose all library features.

### Work in Progress
This project is experimental and still in development.

### Features
- Immutability

Networks are immutable; they are functions.

- Solutions

The model does not impose assumptions—use or build the solution that best fits your needs.

- Composition

Explore the protocol namespace to understand the library's structure and how to contribute with a new neuron and activation rule.

>**Remark:**
The design of the protocols is not yet stable.

**Real Documentation**
Comprehensive documentation is not yet available. For now, refer to the lang namespace for examples.

### Some useful though maybe random references
- [UCLA Automated Reasoning Group video](https://www.youtube.com/watch?v=mavzPKLLCQs&list=PLlDG_zCuBub5AyHuxnw8vfgx7Wd-P-4XN)
- [Algorithmic Game Theory & other interesting stuff](https://www.youtube.com/@csprof/playlists)
- [Common Knowledge of Rationality](https://plato.stanford.edu/entries/common-knowledge/), the SEP page is just amazing
- [SICP original lessons](https://www.youtube.com/watch?v=-J_xL4IGhJA&list=PLE18841CABEA24090), software design for flexibility is a follow-up to this influential book
- [A Course in game theory](https://mitpress.mit.edu/9780262650403/a-course-in-game-theory/), main reference for theoretical game theory. Useful reference to understand how knowledge is modeled in game theory
- [SelfPlay](https://www.youtube.com/watch?v=06VsbwJkrIo) from OpenAI