su postgres -c "createdb wikibrain_en"
su postgres -c "psql wikibrain_en -c CREATE\ USER\ wikibrain\ WITH\ PASSWORD\ \'wikibrain\'\ LOGIN;"
