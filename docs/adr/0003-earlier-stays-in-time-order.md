# Earlier stays in time order instead of stacking by app

Superseded by [ADR 0004](0004-earlier-stacks-an-apps-neighbouring-cards.md): one app's neighbouring cards that arrived close together stack again.

Earlier used to fold each app's notifications into one stack, placed at its newest card's time. Opening that stack put the app's older cards above newer ones from other apps. On the lock screen, a T3 Code stack showed cards from Yesterday above a TikTok card from 7 minutes ago. Chronotif exists because the stock shade orders by app instead of by time, so we chose to give every Earlier notification its own card, newest first, by the time its card shows. Only a Conversation stack still folds cards together, because all its cards belong to one notification.

## Considered Options

- **Stack by app, ordered by the newest member (the previous design)**: keeps Earlier short, but breaks time order as soon as a stack holds cards of different ages.
- **Stack only consecutive cards from one app**: keeps time order, but stacks still form and break apart as other apps post, so cards jump around.

## Consequences

- Earlier is longer when one app has posted many notifications. It stays behind the "N earlier" pill on the lock screen and is dimmed in the panel.
- The website demo mirrors this: Earlier is a flat, newest-first list.
