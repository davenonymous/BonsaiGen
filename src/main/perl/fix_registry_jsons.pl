#!/usr/bin/perl

use v5.40;
use strict;
use warnings;

use Mojo::File;
use JSON::XS;

my $dir = $ARGV[0];
die "Usage: $0 <directory>\n" unless $dir;
die "Directory $dir does not exist\n" unless -d $dir;

my $jsx = JSON::XS->new->utf8->pretty->canonical;
my $json_files = Mojo::File->new($dir)->list_tree->grep(sub { $_->dirname->basename eq 'soiltype' && $_->basename =~ /\.json$/ });

foreach my $file ($json_files->each) {
    my $modId = $file->dirname->dirname->dirname->dirname->dirname->basename;
    my $content = $file->slurp('UTF-8');
    my $data;
    try {
        $data = $jsx->decode($content);
        $data->{'neoforge:conditions'} = [{
            'type' => 'neoforge:mod_loaded',
            'modid' => $modId,
        }];

        $file->spew($jsx->encode($data), , 'UTF-8');
        printf "Added mod loaded condition to %s\n", $file->to_string;
    } catch ($err) {
        warn "Failed to decode JSON from $file: $err";
    };
}
