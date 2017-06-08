su postgres -c "createdb wikibrain_$WIKILANG"
su postgres -c "psql wikibrain_$WIKILANG -c CREATE\ USER\ wikibrain\ WITH\ PASSWORD\ \'wikibrain\'\ LOGIN;"
