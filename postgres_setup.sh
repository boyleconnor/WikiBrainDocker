su postgres -c "createdb wikibrain_en"
su postgres -c "psql wikibrain_en -c CREATE\ USER\ wikibrain;"
su postgres -c "psql wikibrain_en -c ALTER\ USER\ wikibrain\ WITH\ PASSWORD\ \'password\';"
